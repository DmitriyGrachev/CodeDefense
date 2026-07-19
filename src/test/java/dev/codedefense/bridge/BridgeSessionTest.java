package dev.codedefense.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.domain.Verdict;
import dev.codedefense.interview.InterviewConfig;
import dev.codedefense.interview.InterviewEngine;
import dev.codedefense.interview.InterviewScorer;
import dev.codedefense.interview.ReadinessClassifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BridgeSessionTest {
    private final BridgeJsonCodec codec = new BridgeJsonCodec();

    @Test
    void supportsOnlyProtocolVersionsOneAndTwo() {
        assertEquals(BridgeProtocol.VERSION_2, BridgeProtocol.CURRENT_VERSION);
        assertEquals(BridgeProtocol.VERSION_1,
                new BridgeSession(InputStream.nullInputStream(), OutputStream.nullOutputStream()).protocolVersion());
        assertEquals(BridgeProtocol.VERSION_2,
                new BridgeSession(InputStream.nullInputStream(), OutputStream.nullOutputStream(), 2)
                        .protocolVersion());
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeSession(InputStream.nullInputStream(), OutputStream.nullOutputStream(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeSession(InputStream.nullInputStream(), OutputStream.nullOutputStream(), 3));
    }

    @Test
    void validatesPortableEvidenceLocationsAndSafeMetadataOnlyStringForm() {
        BridgeEvidenceLocation location = new BridgeEvidenceLocation("src/main/App.java", 4, 9);

        assertEquals("src/main/App.java", location.relativePath());
        assertTrue(location.toString().contains("src/main/App.java"));
        assertFalse(location.toString().contains("reason"));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("../App.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("src/../App.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("/src/App.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("C:/src/App.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("C:src/App.java", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvidenceLocation("src/A:Stream.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("src\\App.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("src/\u0000App.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("src/\nApp.java", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("src/App.java", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEvidenceLocation("src/App.java", 2, 1));
    }

    @Test
    void protocolTwoPrimaryEvidenceIsSortedUniqueAndImmutable() {
        var first = new BridgeEvidenceLocation("src/A.java", 4, 9);
        var second = new BridgeEvidenceLocation("src/B.java", 2, 3);

        BridgeEvent.QuestionEvent question = new BridgeEvent.QuestionEvent(2, 1, 3, false, "Explain it",
                List.of(second, first));

        assertEquals(List.of(first, second), question.evidence());
        assertThrows(UnsupportedOperationException.class, () -> question.evidence().add(first));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvent.QuestionEvent(2, 1, 3, false, "Explain", List.of(first, first)));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvent.QuestionEvent(2, 1, 3, false, "Explain",
                        java.util.stream.IntStream.rangeClosed(1, 11)
                                .mapToObj(line -> new BridgeEvidenceLocation("src/F" + line + ".java", line, line))
                                .toList()));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvent.QuestionEvent(2, 1, 3, false, "Explain", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvent.QuestionEvent(2, 1, 3, true, "Follow up", List.of(first)));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvent.QuestionEvent(1, 1, 3, false, "Explain", List.of(first)));
    }

    @Test
    void rejectsClientRequestsFromAnotherSupportedProtocol() {
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.writeBytes(codec.encodeRequest(new BridgeRequest.SkipRequest(1)));
        BridgeSession session = new BridgeSession(new ByteArrayInputStream(input.toByteArray()),
                OutputStream.nullOutputStream(), 2);

        BridgeProtocolException exception = assertThrows(BridgeProtocolException.class, session::readRequest);

        assertEquals("Bridge request uses the wrong protocol version.", exception.getMessage());
    }

    @Test
    void emitsDryRunOrderWithoutReadingConfirmation() {
        Fixture fixture = fixture();

        fixture.session.emit(new BridgeEvent.HelloEvent(1, List.of("interactiveDefenseV1")));
        fixture.session.emit(new BridgeEvent.PreviewEvent(1, "demo", "Staged change", "balanced", 1, 2, 0));
        fixture.session.emit(new BridgeEvent.CompletedEvent(1, 0, false));

        assertEquals(List.of("HelloEvent", "PreviewEvent", "CompletedEvent"), eventNames(fixture.events()));
        assertFalse(((BridgeEvent.CompletedEvent) fixture.events().get(2)).codexInvoked());
    }

    @Test
    void confirmationEmitsPromptAndAcceptsExplicitResponse() {
        Fixture fixture = fixture(new BridgeRequest.ConfirmRequest(1, true));

        boolean accepted = new BridgeConfirmationPrompt(fixture.session).confirm("Send bounded content?");

        assertEquals(true, accepted);
        assertInstanceOf(BridgeEvent.ConfirmationRequiredEvent.class, fixture.events().getFirst());
    }

    @Test
    void eofAndCancelAreSafeDeclinesBeforeDisclosure() {
        assertFalse(new BridgeConfirmationPrompt(fixture().session).confirm("Send bounded content?"));
        assertFalse(new BridgeConfirmationPrompt(fixture(new BridgeRequest.CancelRequest(1)).session)
                .confirm("Send bounded content?"));
    }

    @Test
    void acceptedThreeQuestionInterviewUsesExistingEngineAndEmitsOrderedEvents() {
        Fixture fixture = fixture(answer("one"), answer("two"), answer("three"));
        AtomicInteger evaluations = new AtomicInteger();
        InterviewEngine engine = engine(fixture, request -> {
            evaluations.incrementAndGet();
            return correct(90);
        });

        var result = engine.conduct(analysis());

        assertEquals(3, evaluations.get());
        assertEquals(90, result.overallScore());
        assertEquals(List.of(
                "QuestionEvent", "EvaluationEvent", "QuestionScoreEvent",
                "QuestionEvent", "EvaluationEvent", "QuestionScoreEvent",
                "QuestionEvent", "EvaluationEvent", "QuestionScoreEvent", "SummaryEvent"),
                eventNames(fixture.events()));
    }

    @Test
    void followUpAndSkipsMapToExistingInterviewSemantics() {
        Fixture fixture = fixture(answer("partial"), answer("follow-up"),
                new BridgeRequest.SkipRequest(1), new BridgeRequest.SkipRequest(1));
        AtomicInteger evaluations = new AtomicInteger();
        InterviewEngine engine = engine(fixture, request -> {
            if (evaluations.getAndIncrement() == 0) {
                return new AnswerEvaluation(Verdict.PARTIAL, 40, "Needs one detail.", List.of("boundary"),
                        List.of("cleanup"), Optional.of("How is cleanup bounded?"));
            }
            return correct(80);
        });

        var result = engine.conduct(analysis());

        assertEquals(2, evaluations.get());
        assertEquals(2, result.skippedQuestionCount());
        List<BridgeEvent> events = fixture.events();
        long questions = events.stream().filter(BridgeEvent.QuestionEvent.class::isInstance).count();
        long skipped = events.stream().filter(BridgeEvent.EvaluationEvent.class::isInstance)
                .map(BridgeEvent.EvaluationEvent.class::cast)
                .filter(event -> event.verdict().equals("SKIPPED"))
                .count();
        assertEquals(4, questions);
        assertEquals(2, skipped);
    }

    @Test
    void skipRequestBecomesExactSkipAndCancelOrEofCancelActiveInterview() {
        assertEquals("skip", new BridgeInterviewInput(fixture(new BridgeRequest.SkipRequest(1)).session)
                .readAnswer("> "));
        assertEquals(null, new BridgeInterviewInput(fixture(new BridgeRequest.CancelRequest(1)).session)
                .readAnswer("> "));
        assertEquals(null, new BridgeInterviewInput(fixture().session).readAnswer("> "));
    }

    @Test
    void invalidClientMessageFailsSafelyWithoutAnswerContent() {
        Fixture fixture = fixture(answer("PRIVATE-ANSWER"));

        BridgeProtocolException exception = assertThrows(BridgeProtocolException.class,
                () -> new BridgeConfirmationPrompt(fixture.session).confirm("Confirm?"));

        assertEquals("Unexpected bridge request for confirmation.", exception.getMessage());
        assertFalse(exception.getMessage().contains("PRIVATE-ANSWER"));
    }

    @Test
    void successfulPassportAndModelFailureHaveStableTerminalEvents() {
        Fixture fixture = fixture();

        fixture.session.emit(new BridgeEvent.PassportSavedEvent(1, "passport.md", "CURRENT", "abc123"));
        fixture.session.emit(new BridgeEvent.CompletedEvent(1, 0, true));
        fixture.session.emit(new BridgeEvent.ErrorEvent(1, "MODEL_FAILURE", "Codex execution failed.", 7));

        assertEquals(List.of("PassportSavedEvent", "CompletedEvent", "ErrorEvent"), eventNames(fixture.events()));
    }

    private InterviewEngine engine(Fixture fixture, dev.codedefense.interview.AnswerEvaluator evaluator) {
        return new InterviewEngine(evaluator, new BridgeInterviewInput(fixture.session),
                new BridgeInterviewOutput(fixture.session), new InterviewScorer(), new ReadinessClassifier(),
                InterviewConfig.defaults());
    }

    private Fixture fixture(BridgeRequest... requests) {
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        for (BridgeRequest request : requests) {
            input.writeBytes(codec.encodeRequest(request));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return new Fixture(new BridgeSession(new ByteArrayInputStream(input.toByteArray()), output, codec), output);
    }

    private BridgeRequest answer(String value) {
        return new BridgeRequest.AnswerRequest(1, value);
    }

    private List<String> eventNames(List<BridgeEvent> events) {
        return events.stream().map(event -> event.getClass().getSimpleName()).toList();
    }

    private ProjectAnalysis analysis() {
        List<TechnicalQuestion> questions = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            questions.add(new TechnicalQuestion("q" + index, "Question " + index + "?", "goal",
                    List.of("one", "two"), List.of(new CodeEvidence("src/App.java", 1, 1, "reason"))));
        }
        return new ProjectAnalysis("demo", "Java", "summary", List.of("start", "finish"),
                List.of(new ProjectComponent("App", "service", "runs", List.of("src/App.java"))),
                List.of("boundary", "cleanup"), questions);
    }

    private AnswerEvaluation correct(int score) {
        return new AnswerEvaluation(Verdict.CORRECT, score, "Correct.", List.of("boundary"), List.of(),
                Optional.empty());
    }

    private record Fixture(BridgeSession session, ByteArrayOutputStream output) {
        List<BridgeEvent> events() {
            BridgeJsonCodec codec = new BridgeJsonCodec();
            String[] lines = output.toString(java.nio.charset.StandardCharsets.UTF_8).split("\n");
            List<BridgeEvent> events = new ArrayList<>();
            for (String line : lines) {
                if (!line.isEmpty()) {
                    events.add(codec.decodeEvent((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
            return List.copyOf(events);
        }
    }
}
