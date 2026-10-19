package com.example.experiment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

import com.example.experiment_service.service.ChuteIdParser;

/** Plain unit test: no Spring, no database. It runs in milliseconds. */
class ChuteIdParserTest {

    @Test
    void parsesPlainList() {
        assertThat(ChuteIdParser.parse("1,2,3", true)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void parsesListWithBracketsAndSpaces() {
        assertThat(ChuteIdParser.parse("[1, 2 , 3]", true)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void nullBlankAndEmptyBracketsGiveEmptyList() {
        assertThat(ChuteIdParser.parse(null, true)).isEmpty();
        assertThat(ChuteIdParser.parse("   ", true)).isEmpty();
        assertThat(ChuteIdParser.parse("[]", true)).isEmpty();
    }

    @Test
    void emptyPartsBetweenCommasAreSkipped() {
        assertThat(ChuteIdParser.parse("1,,2,", true)).isEqualTo(List.of(1, 2));
    }

    @Test
    void strictModeRejectsBadValue() {
        assertThatThrownBy(() -> ChuteIdParser.parse("1,abc", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void lenientModeSkipsBadValue() {
        assertThat(ChuteIdParser.parse("1,abc,3", false)).isEqualTo(List.of(1, 3));
    }

    @Test
    void decimalNumberIsNotAChuteId() {
        assertThatThrownBy(() -> ChuteIdParser.parse("1.5", true)).isInstanceOf(IllegalArgumentException.class);
    }
}