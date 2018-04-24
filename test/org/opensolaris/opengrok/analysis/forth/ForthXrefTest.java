/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.forth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.BeforeClass;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.WriteXrefArgs;
import org.opensolaris.opengrok.analysis.Xrefer;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import static org.opensolaris.opengrok.util.CustomAssertions.assertLinesEqual;
import static org.opensolaris.opengrok.util.StreamUtils.copyStream;
import static org.opensolaris.opengrok.util.StreamUtils.readTagsFromResource;

/**
 * Tests the {@link ForthXref} class.
 */
public class ForthXrefTest {

    private static RuntimeEnvironment env;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();
    }

    @Test
    public void sampleTest() throws IOException {
        writeAndCompare("org/opensolaris/opengrok/analysis/forth/sample.fs",
            "org/opensolaris/opengrok/analysis/forth/sample_xref.html",
            readTagsFromResource(
                "org/opensolaris/opengrok/analysis/forth/sampletags"), 287);
    }

    @Test
    public void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare("org/opensolaris/opengrok/analysis/forth/truncated.fs",
            "org/opensolaris/opengrok/analysis/forth/truncated_xref.html",
            null, 1);
    }

    private void writeAndCompare(String sourceResource, String resultResource,
        Definitions defs, int expLOC) throws IOException {

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            sourceResource);
        assertNotNull(sourceResource + " should get-as-stream", res);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int actLOC = writeForthXref(new PrintStream(baos), res, defs);
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            resultResource);
        assertNotNull(resultResource + " should get-as-stream", exp);
        byte[] expraw = copyStream(exp);
        exp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String gotten[] = ostr.split("\n");

        String estr = new String(expraw, "UTF-8");
        String expected[] = estr.split("\n");

        assertLinesEqual("Forth xref", expected, gotten);
        assertEquals("Forth LOC", expLOC, actLOC);
    }

    private int writeForthXref(PrintStream oss, InputStream iss,
        Definitions defs) throws IOException {

        oss.print(getHtmlBegin());

        Writer sw = new StringWriter();
        ForthAnalyzerFactory fac = new ForthAnalyzerFactory(env);
        FileAnalyzer analyzer = fac.getAnalyzer();
        analyzer.setScopesEnabled(true);
        analyzer.setFoldingEnabled(true);
        WriteXrefArgs wargs = new WriteXrefArgs(
            new InputStreamReader(iss, "UTF-8"), sw);
        wargs.setDefs(defs);
        Xrefer xref = analyzer.writeXref(wargs);
        oss.print(sw.toString());

        oss.print(getHtmlEnd());
        return xref.getLOC();
    }

    private static String getHtmlBegin() {
        return "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "<meta charset=\"UTF-8\">\n" +
            "<title>sampleFile - OpenGrok cross reference" +
            " for /sampleFile</title></head><body>\n";
    }

    private static String getHtmlEnd() {
        return "</body>\n" +
            "</html>\n";
    }
}