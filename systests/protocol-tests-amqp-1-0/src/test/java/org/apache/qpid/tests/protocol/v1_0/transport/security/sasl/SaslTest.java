/*
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

package org.apache.qpid.tests.protocol.v1_0.transport.security.sasl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.Before;
import org.junit.Test;

import org.apache.qpid.server.protocol.v1_0.framing.SASLFrame;
import org.apache.qpid.server.protocol.v1_0.framing.TransportFrame;
import org.apache.qpid.server.protocol.v1_0.type.Binary;
import org.apache.qpid.server.protocol.v1_0.type.Symbol;
import org.apache.qpid.server.protocol.v1_0.type.security.SaslChallenge;
import org.apache.qpid.server.protocol.v1_0.type.security.SaslCode;
import org.apache.qpid.server.protocol.v1_0.type.security.SaslInit;
import org.apache.qpid.server.protocol.v1_0.type.security.SaslMechanisms;
import org.apache.qpid.server.protocol.v1_0.type.security.SaslOutcome;
import org.apache.qpid.server.protocol.v1_0.type.transport.Open;
import org.apache.qpid.tests.protocol.SpecificationTest;
import org.apache.qpid.tests.protocol.v1_0.FrameEncoder;
import org.apache.qpid.tests.protocol.v1_0.FrameTransport;
import org.apache.qpid.tests.protocol.v1_0.Interaction;
import org.apache.qpid.tests.utils.BrokerAdmin;
import org.apache.qpid.tests.utils.BrokerAdminUsingTestBase;

public class SaslTest extends BrokerAdminUsingTestBase
{
    private static final Symbol CRAM_MD5 = Symbol.getSymbol("CRAM-MD5");
    private static final Symbol PLAIN = Symbol.getSymbol("PLAIN");

    private static final byte[] SASL_AMQP_HEADER_BYTES = "AMQP\3\1\0\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AMQP_HEADER_BYTES = "AMQP\0\1\0\0".getBytes(StandardCharsets.UTF_8);
    private String _username;
    private String _password;

    @Before
    public void setUp()
    {
        assumeThat(getBrokerAdmin().isSASLSupported(), is(true));
        assumeThat(getBrokerAdmin().isSASLMechanismSupported(PLAIN.toString()), is(true));
        assumeThat(getBrokerAdmin().isSASLMechanismSupported(CRAM_MD5.toString()), is(true));
        _username = getBrokerAdmin().getValidUsername();
        _password = getBrokerAdmin().getValidPassword();
    }

    @Test
    @SpecificationTest(section = "5.3.2",
            description = "SASL Negotiation [...] challenge/response step occurs zero times")
    public void saslSuccessfulAuthentication() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            final Interaction interaction = transport.newInteraction();
            final byte[] saslHeaderResponse = interaction.protocolHeader(SASL_AMQP_HEADER_BYTES)
                                                         .negotiateProtocol().consumeResponse()
                                                         .getLatestResponse(byte[].class);
            assertThat(saslHeaderResponse, is(equalTo(SASL_AMQP_HEADER_BYTES)));

            SaslMechanisms saslMechanismsResponse = interaction.consumeResponse().getLatestResponse(SaslMechanisms.class);
            assertThat(Arrays.asList(saslMechanismsResponse.getSaslServerMechanisms()), hasItem(PLAIN));

            final Binary initialResponse = new Binary(String.format("\0%s\0%s", _username, _password).getBytes(StandardCharsets.US_ASCII));
            SaslOutcome saslOutcome = interaction.saslMechanism(PLAIN)
                                                 .saslInitialResponse(initialResponse)
                                                 .saslInit().consumeResponse()
                                                 .getLatestResponse(SaslOutcome.class);
            assertThat(saslOutcome.getCode(), equalTo(SaslCode.OK));

            final byte[] headerResponse = interaction.protocolHeader(AMQP_HEADER_BYTES)
                                                     .negotiateProtocol().consumeResponse()
                                                     .getLatestResponse(byte[].class);
            assertThat(headerResponse, is(equalTo(AMQP_HEADER_BYTES)));

            transport.assertNoMoreResponses();
        }
    }

    @Test
    @SpecificationTest(section = "2.4.2",
            description = "For applications that use many short-lived connections,"
                          + " it MAY be desirable to pipeline the connection negotiation process."
                          + " A peer MAY do this by starting to send subsequent frames before receiving"
                          + " the partner’s connection header or open frame")
    public void saslSuccessfulAuthenticationWithPipelinedFrames() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            final Interaction interaction = transport.newInteraction();
            FrameEncoder frameEncoder = new FrameEncoder();

            SaslInit saslInit = new SaslInit();
            saslInit.setMechanism(PLAIN);
            saslInit.setInitialResponse(new Binary(String.format("\0%s\0%s", _username, _password)
                                                         .getBytes(StandardCharsets.US_ASCII)));
            ByteBuffer saslInitByteBuffer = frameEncoder.encode(new SASLFrame(saslInit));

            Open open = new Open();
            open.setContainerId("containerId");
            ByteBuffer openByteBuffer = frameEncoder.encode(new TransportFrame(0, open));

            int initSize = saslInitByteBuffer.remaining();
            int openSize = openByteBuffer.remaining();
            int dataLength = SASL_AMQP_HEADER_BYTES.length + AMQP_HEADER_BYTES.length + initSize + openSize;
            byte[] data = new byte[dataLength];

            System.arraycopy(SASL_AMQP_HEADER_BYTES, 0, data, 0, SASL_AMQP_HEADER_BYTES.length);
            saslInitByteBuffer.get(data, SASL_AMQP_HEADER_BYTES.length, initSize);
            System.arraycopy(AMQP_HEADER_BYTES,
                             0,
                             data,
                             SASL_AMQP_HEADER_BYTES.length + initSize,
                             AMQP_HEADER_BYTES.length);
            openByteBuffer.get(data, SASL_AMQP_HEADER_BYTES.length + AMQP_HEADER_BYTES.length + initSize, openSize);

            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
            buffer.writeBytes(data);

            transport.sendPerformative(buffer);


            final byte[] saslHeaderResponse = interaction.consumeResponse().getLatestResponse(byte[].class);
            assertThat(saslHeaderResponse, is(equalTo(SASL_AMQP_HEADER_BYTES)));

            SaslMechanisms saslMechanismsResponse = interaction.consumeResponse().getLatestResponse(SaslMechanisms.class);
            assertThat(Arrays.asList(saslMechanismsResponse.getSaslServerMechanisms()), hasItem(PLAIN));

            SaslOutcome saslOutcome = interaction.consumeResponse().getLatestResponse(SaslOutcome.class);
            assertThat(saslOutcome.getCode(), equalTo(SaslCode.OK));

            final byte[] headerResponse = interaction.consumeResponse().getLatestResponse(byte[].class);
            assertThat(headerResponse, is(equalTo(AMQP_HEADER_BYTES)));

            interaction.consumeResponse().getLatestResponse(Open.class);
            interaction.doCloseConnection();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2",
            description = "SASL Negotiation [...] challenge/response step occurs once")
    public void saslSuccessfulAuthenticationWithChallengeResponse() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            final Interaction interaction = transport.newInteraction();
            final byte[] saslHeaderResponse = interaction.protocolHeader(SASL_AMQP_HEADER_BYTES)
                                                         .negotiateProtocol().consumeResponse()
                                                         .getLatestResponse(byte[].class);
            assertThat(saslHeaderResponse, is(equalTo(SASL_AMQP_HEADER_BYTES)));

            SaslMechanisms saslMechanismsResponse = interaction.consumeResponse().getLatestResponse(SaslMechanisms.class);
            assertThat(Arrays.asList(saslMechanismsResponse.getSaslServerMechanisms()), hasItem(CRAM_MD5));

            SaslChallenge saslChallenge = interaction.saslMechanism(CRAM_MD5)
                                                     .saslInit().consumeResponse()
                                                     .getLatestResponse(SaslChallenge.class);
            assertThat(saslChallenge.getChallenge(), is(notNullValue()));

            byte[] response = generateCramMD5ClientResponse(_username, _password,
                                                            saslChallenge.getChallenge().getArray());

            final SaslOutcome saslOutcome = interaction.saslResponseResponse(new Binary(response))
                                                       .saslResponse()
                                                       .consumeResponse()
                                                       .getLatestResponse(SaslOutcome.class);
            assertThat(saslOutcome.getCode(), equalTo(SaslCode.OK));

            final byte[] headerResponse = interaction.protocolHeader(AMQP_HEADER_BYTES)
                                                     .negotiateProtocol()
                                                     .consumeResponse()
                                                     .getLatestResponse(byte[].class);
            assertThat(headerResponse, is(equalTo(AMQP_HEADER_BYTES)));

            transport.assertNoMoreResponses();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2", description = "SASL Negotiation")
    public void saslUnsuccessfulAuthentication() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            final Interaction interaction = transport.newInteraction();
            final byte[] saslHeaderResponse = interaction.protocolHeader(SASL_AMQP_HEADER_BYTES)
                                                         .negotiateProtocol().consumeResponse()
                                                         .getLatestResponse(byte[].class);
            assertThat(saslHeaderResponse, is(equalTo(SASL_AMQP_HEADER_BYTES)));

            SaslMechanisms saslMechanismsResponse = interaction.consumeResponse().getLatestResponse(SaslMechanisms.class);
            assertThat(Arrays.asList(saslMechanismsResponse.getSaslServerMechanisms()), hasItem(PLAIN));

            final Binary initialResponse =
                    new Binary(String.format("\0%s\0badpassword", _username).getBytes(StandardCharsets.US_ASCII));
            SaslOutcome saslOutcome = interaction.saslMechanism(PLAIN)
                                                 .saslInitialResponse(initialResponse)
                                                 .saslInit().consumeResponse()
                                                 .getLatestResponse(SaslOutcome.class);
            assertThat(saslOutcome.getCode(), equalTo(SaslCode.AUTH));

            transport.assertNoMoreResponsesAndChannelClosed();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2",
            description = "The partner MUST then choose one of the supported mechanisms and initiate a sasl exchange."
                          + "If the selected mechanism is not supported by the receiving peer, it MUST close the connection "
                          + "with the authentication-failure close-code.")
    public void unsupportedSaslMechanism() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            final Interaction interaction = transport.newInteraction();
            final byte[] saslHeaderResponse = interaction.protocolHeader(SASL_AMQP_HEADER_BYTES)
                                                         .negotiateProtocol().consumeResponse()
                                                         .getLatestResponse(byte[].class);
            assertThat(saslHeaderResponse, is(equalTo(SASL_AMQP_HEADER_BYTES)));

            interaction.consumeResponse(SaslMechanisms.class);

            SaslOutcome saslOutcome = interaction.saslMechanism(Symbol.getSymbol("NOT-A-MECHANISM"))
                                                 .saslInit().consumeResponse()
                                                 .getLatestResponse(SaslOutcome.class);
            assertThat(saslOutcome.getCode(), equalTo(SaslCode.AUTH));
            assertThat(saslOutcome.getAdditionalData(), is(nullValue()));

            transport.assertNoMoreResponsesAndChannelClosed();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2", description = "SASL Negotiation")
    public void authenticationBypassDisallowed() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            final Interaction interaction = transport.newInteraction();
            final byte[] saslHeaderResponse = interaction.protocolHeader(SASL_AMQP_HEADER_BYTES)
                                                         .negotiateProtocol().consumeResponse()
                                                         .getLatestResponse(byte[].class);
            assertThat(saslHeaderResponse, is(equalTo(SASL_AMQP_HEADER_BYTES)));

            interaction.consumeResponse(SaslMechanisms.class);
            interaction.open().sync();

            transport.assertNoMoreResponsesAndChannelClosed();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2",
            description = "The peer acting as the SASL server MUST announce supported authentication mechanisms using"
                          + "the sasl-mechanisms frame.")
    public void clientSendsSaslMechanisms() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            SaslMechanisms clientMechs = new SaslMechanisms();
            clientMechs.setSaslServerMechanisms(new Symbol[] {Symbol.valueOf("CLIENT-MECH")});
            transport.newInteraction()
                     .protocolHeader(SASL_AMQP_HEADER_BYTES)
                     .negotiateProtocol().consumeResponse()
                     .consumeResponse(SaslMechanisms.class)
                     .sendPerformative(clientMechs)
                     .sync();

            transport.assertNoMoreResponsesAndChannelClosed();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2", description = "SASL Negotiation")
    public void clientSendsSaslChallenge() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            SaslChallenge saslChallenge = new SaslChallenge();
            saslChallenge.setChallenge(new Binary(new byte[] {}));
            transport.newInteraction()
                     .protocolHeader(SASL_AMQP_HEADER_BYTES)
                     .negotiateProtocol().consumeResponse()
                     .consumeResponse(SaslMechanisms.class)
                     .sendPerformative(saslChallenge)
                     .sync();

            transport.assertNoMoreResponsesAndChannelClosed();
        }
    }

    @Test
    @SpecificationTest(section = "5.3.2", description = "SASL Negotiation")
    public void clientSendsSaslOutcome() throws Exception
    {
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.AMQP);
        try (FrameTransport transport = new FrameTransport(addr, true).connect())
        {
            SaslOutcome saslOutcome = new SaslOutcome();
            saslOutcome.setCode(SaslCode.OK);
            transport.newInteraction()
                     .protocolHeader(SASL_AMQP_HEADER_BYTES)
                     .negotiateProtocol().consumeResponse()
                     .consumeResponse(SaslMechanisms.class)
                     .sendPerformative(saslOutcome)
                     .sync();

            transport.assertNoMoreResponsesAndChannelClosed();
        }
    }

    private static byte[] generateCramMD5ClientResponse(String userName, String userPassword, byte[] challengeBytes)
            throws Exception
    {
        String macAlgorithm = "HmacMD5";
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(userPassword.getBytes(StandardCharsets.UTF_8), macAlgorithm));
        final byte[] messageAuthenticationCode = mac.doFinal(challengeBytes);
        String responseAsString = userName + " " + DatatypeConverter.printHexBinary(messageAuthenticationCode)
                                                                    .toLowerCase();
        return responseAsString.getBytes();
    }
}