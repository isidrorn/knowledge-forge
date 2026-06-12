package io.irn.aipipeline.processing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleContentPreprocessorTest {

    private final ArticleContentPreprocessor preprocessor = new ArticleContentPreprocessor();

    @Test
    void prepare_collapsesExtraSpacesAndTabs() {
        String result = preprocessor.prepare("hello   \t  world");
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void prepare_collapsesExcessiveNewlines() {
        String result = preprocessor.prepare("line1\n\n\n\nline2");
        assertThat(result).isEqualTo("line1\n\nline2");
    }

    @Test
    void prepare_stripsLeadingAndTrailingWhitespace() {
        String result = preprocessor.prepare("  content  ");
        assertThat(result).isEqualTo("content");
    }

    @Test
    void isTooLarge_returnsFalse_whenUnderLimit() {
        assertThat(preprocessor.isTooLarge("short", 100)).isFalse();
    }

    @Test
    void isTooLarge_returnsTrue_whenOverLimit() {
        String content = "x".repeat(101);
        assertThat(preprocessor.isTooLarge(content, 100)).isTrue();
    }

    @Test
    void isTooLarge_returnsFalse_whenNull() {
        assertThat(preprocessor.isTooLarge(null, 100)).isFalse();
    }
}
