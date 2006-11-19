/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.exchange;

import java.util.Map;
import java.util.HashMap;

import junit.framework.TestCase;

/**
 */
public class HeadersBindingTest extends TestCase
{
    private Map<String, String> bindHeaders = new HashMap<String, String>();
    private Map<String, String> matchHeaders = new HashMap<String, String>();

    public void testDefault_1()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testDefault_2()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testDefault_3()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Altered value of A");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_1()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_2()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_3()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_4()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");
        matchHeaders.put("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_5()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Altered value of B");
        matchHeaders.put("C", "Value of C");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_1()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_2()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_3()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_4()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");
        matchHeaders.put("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_5()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Altered value of B");
        matchHeaders.put("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_6()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Altered value of A");
        matchHeaders.put("B", "Altered value of B");
        matchHeaders.put("C", "Value of C");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(HeadersBindingTest.class);
    }
}
