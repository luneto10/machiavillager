package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrivateChannelTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "@m vem aqui|vem aqui",
        "@M cadê você?|cadê você?",
        "@machi   where are you|where are you",
        "@MachiaVillager come here|come here",
        "  @m  spaced out  |spaced out",
    })
    void prefixedLine_isPrivate_andLosesThePrefix(String text, String body) {
        assertEquals(Optional.of(body), PrivateChannel.body(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@m", "@machi   "})
    void prefixAlone_isPrivate_withNothingToSend(String text) {
        assertEquals(Optional.of(""), PrivateChannel.body(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@me too", "@mike hi", "@machine", "hi @m", "machi vem aqui", "m vem aqui", ""})
    void otherLines_stayPublic(String text) {
        assertEquals(Optional.empty(), PrivateChannel.body(text));
    }
}
