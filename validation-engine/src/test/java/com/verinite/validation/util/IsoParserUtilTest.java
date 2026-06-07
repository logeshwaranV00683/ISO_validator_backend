package com.verinite.validation.util;

import com.verinite.validation.iso.ParsedMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsoParserUtilTest {

    private static final String HEX_0200 =
            "30323030" +
                    "33323230303030303030303030303030" +
                    "313634313131313131313131313131313131" +
                    "303030303030" +
                    "303030303030303130303030" +
                    "303030303031";

    private static final String PACKAGER_PATH =
            "packager/iso87ascii.xml";

    @Test
    void parse_0200_mtiCorrect() throws Exception {
        ParsedMessage msg = IsoParserUtil.parse(HEX_0200, PACKAGER_PATH);

        assertThat(msg.getMti()).isEqualTo("0200");
    }

    @Test
    void parse_0200_de3_present() throws Exception {
        ParsedMessage msg = IsoParserUtil.parse(HEX_0200, PACKAGER_PATH);

        assertThat(msg.getFields().get(3))
                .isEqualTo("164111");
    }

    @Test
    void parse_0200_de4_present() throws Exception {
        ParsedMessage msg = IsoParserUtil.parse(HEX_0200, PACKAGER_PATH);

        assertThat(msg.getFields().get(4))
                .isEqualTo("111111111111");
    }

    @Test
    void parse_0200_de7_present() throws Exception {
        ParsedMessage msg = IsoParserUtil.parse(HEX_0200, PACKAGER_PATH);

        assertThat(msg.getFields().get(7))
                .isEqualTo("0000000000");
    }

    @Test
    void parse_0200_de11_present() throws Exception {
        ParsedMessage msg = IsoParserUtil.parse(HEX_0200, PACKAGER_PATH);

        assertThat(msg.getFields().get(11))
                .isEqualTo("000100");
    }

    @Test
    void parse_invalidHex_throwsISOException() {
        assertThatThrownBy(() ->
                IsoParserUtil.parse("XXXXXXXX", PACKAGER_PATH))
                .isInstanceOf(org.jpos.iso.ISOException.class);
    }

    @Test
    void parse_emptyString_throwsISOException() {
        assertThatThrownBy(() ->
                IsoParserUtil.parse("", PACKAGER_PATH))
                .isInstanceOf(Exception.class);
    }

    @Test
    void debugMessage() throws Exception {
        ParsedMessage msg = IsoParserUtil.parse(HEX_0200, PACKAGER_PATH);

        System.out.println("MTI = " + msg.getMti());

        msg.getFields().forEach((k, v) ->
                System.out.println("DE" + k + " = " + v));
    }
}