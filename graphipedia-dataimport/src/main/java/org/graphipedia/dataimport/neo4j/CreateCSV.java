//
// Copyright (c) 2012 Mirko Nasato
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
// OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
// ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.
//
package org.graphipedia.dataimport.neo4j;

import com.opencsv.CSVWriter;
import org.graphipedia.dataimport.SimpleStaxParser;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class CreateCSV {

    private final Map<String,Integer> inMemoryIndex;
    private final String dataDir;
    private final String inputFile;

    public CreateCSV(String dataDir, String inputFile) throws IOException {
        this.dataDir = dataDir;
        this.inputFile = inputFile;
        inMemoryIndex = new HashMap<>(13_000_000, 0.95f);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("USAGE: CreateCSV <input-file> <data-dir>");
            System.exit(255);
        }
        String inputFile = args[0];
        String dataDir = args[1];
        CreateCSV importer = new CreateCSV(dataDir,inputFile);
        importer.createNodes();
        importer.createRelationships();
    }

    public void createNodes() throws Exception {
        System.out.println("Creating pages CSV...");
        try (CSVWriter writer = csvWriter(dataDir, "pages")) {
            long startTime = System.currentTimeMillis();
            NodeParser nodeCreator = new NodeParser(writer);
            writer.writeNext(new String[]{":ID","title"});
            nodeCreator.parse(inputFile);
            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf("\n%d pages written in %d seconds.\n", nodeCreator.pageCount, elapsedSeconds);
        }
    }

    private CSVWriter csvWriter(String dataDir, String fileName) throws IOException {
        File file = new File(dataDir, fileName+".csv.gz");
        Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file),1_000_000),"UTF-8");
        return new CSVWriter(writer, ',', '"');
    }

    public void createRelationships() throws Exception {
        System.out.println("Creating CSV for links...");
        try (CSVWriter writer = csvWriter(dataDir, "links")) {
            long startTime = System.currentTimeMillis();
            writer.writeNext(new String[]{":START_ID",":END_ID"});
            RelationshipParser relationshipCreator = new RelationshipParser(writer);
            relationshipCreator.parse(inputFile);
            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf("\n%d links written in %d seconds; %d broken links ignored\n",
                    relationshipCreator.linkCount, elapsedSeconds, relationshipCreator.badLinkCount);
        }
    }

    private class NodeParser extends SimpleStaxParser {
        private final CSVWriter writer;
        String[] line = new String[2];
        int pageCount;

        public NodeParser(CSVWriter writer) {
            super(singletonList("t"));
            this.writer = writer;
        }

        @Override
        protected void handleElement(String element, String value) {
            int len = value.length();
            if (len ==0) return;
            value = escape(value, len);
            if (inMemoryIndex.containsKey(value)) return;
            line[0] = String.valueOf(pageCount);
            line[1] = value;
            writer.writeNext(line);
            inMemoryIndex.put(value,pageCount);
            pageCount++;
        }
    }

    private static String escape(String value, int len) {
        if (value.charAt(len-1)=='\\') value = value.substring(0,len-1);
        if (value.indexOf('\\')!=-1) value = value.replaceAll("\\\\","\\\\\\\\");
        return value;
    }

    private class RelationshipParser extends SimpleStaxParser {
        private final CSVWriter writer;
        String[] line = new String[2];
        int linkCount;
        int badLinkCount;

        public RelationshipParser(CSVWriter writer) {
            super(asList("t", "l"));
            this.writer = writer;
        }

        @Override
        protected void handleElement(String element, String value) {
            int len = value.length();
            if (len ==0) return;
            value = escape(value,len);
            if (element.charAt(0) == 't') line[0] = String.valueOf(inMemoryIndex.get(value));
            else {
                if (inMemoryIndex.containsKey(value)) {
                    line[1]=String.valueOf(inMemoryIndex.get(value));
                    writer.writeNext(line);
                    linkCount++;
                } else {
                    badLinkCount++;
                }
            }
        }
    }
}
