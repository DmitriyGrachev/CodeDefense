package dev.codedefense.ci;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PassportTrailerParserTest {
    private static final String HASH = "a".repeat(64);
    private final PassportTrailerParser parser = new PassportTrailerParser();

    @Test
    void acceptsOneStrictTrailerInTheFinalParagraph() {
        PassportTrailer trailer = parser.parse("Subject\n\nBody\n\nCodeDefense-Passport: sha256:" + HASH + "\n");

        assertEquals(PassportTrailer.State.VALID, trailer.state());
        assertEquals(HASH, trailer.fingerprint());
    }

    @Test
    void distinguishesMissingFromMalformedOrDuplicateTrailers() {
        assertEquals(PassportTrailer.State.MISSING, parser.parse("Subject only\n").state());
        assertEquals(PassportTrailer.State.MISSING,
                parser.parse("CodeDefense-Passport: sha256:" + HASH + "\n\nBody\n").state());
        assertEquals(PassportTrailer.State.MALFORMED,
                parser.parse("Subject\n\nCodeDefense-Passport: sha256:bad\n").state());
        assertEquals(PassportTrailer.State.MALFORMED,
                parser.parse("Subject\n\ncodedefense-passport: sha256:" + HASH + "\n").state());
        assertEquals(PassportTrailer.State.MALFORMED, parser.parse("Subject\n\n"
                + "CodeDefense-Passport: sha256:" + HASH + "\n"
                + "CodeDefense-Passport: sha256:" + "b".repeat(64) + "\n").state());
    }
}
