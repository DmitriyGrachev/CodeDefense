package dev.codedefense.bridge;

import java.util.List;

/** Messages emitted to an IDE adapter. */
public sealed interface BridgeEvent permits BridgeEvent.HelloEvent, BridgeEvent.PreviewEvent,
        BridgeEvent.ConfirmationRequiredEvent, BridgeEvent.QuestionEvent, BridgeEvent.EvaluationEvent,
        BridgeEvent.QuestionScoreEvent, BridgeEvent.SummaryEvent, BridgeEvent.PassportSavedEvent,
        BridgeEvent.ProvenanceEvent, BridgeEvent.CompletedEvent, BridgeEvent.ErrorEvent {
    int protocolVersion();

    record HelloEvent(int protocolVersion, List<String> capabilities) implements BridgeEvent {
        public HelloEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            capabilities = BridgeProtocol.copyStrings(capabilities, "capabilities",
                    BridgeProtocol.MAX_CAPABILITIES, 64);
        }
    }

    record PreviewEvent(int protocolVersion, String projectName, String changeKind, String focus,
            int selectedFiles, int addedLines, int deletedLines) implements BridgeEvent {
        public PreviewEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            projectName = BridgeProtocol.requireText(projectName, "projectName", 512);
            changeKind = BridgeProtocol.requireText(changeKind, "changeKind", 64);
            focus = BridgeProtocol.requireText(focus, "focus", 64);
            BridgeProtocol.requireRange(selectedFiles, 0, 30, "selectedFiles");
            BridgeProtocol.requireRange(addedLines, 0, Integer.MAX_VALUE, "addedLines");
            BridgeProtocol.requireRange(deletedLines, 0, Integer.MAX_VALUE, "deletedLines");
        }
    }

    record ConfirmationRequiredEvent(int protocolVersion, String message) implements BridgeEvent {
        public ConfirmationRequiredEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            message = BridgeProtocol.requireText(message, "message", 1024);
        }
    }

    record QuestionEvent(int protocolVersion, int number, int total, boolean followUp, String prompt)
            implements BridgeEvent {
        public QuestionEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            BridgeProtocol.requireRange(number, 1, 3, "number");
            BridgeProtocol.requireRange(total, 1, 3, "total");
            if (number > total) {
                throw new IllegalArgumentException("number cannot exceed total");
            }
            prompt = BridgeProtocol.requireText(prompt, "prompt", 16_384);
        }

        @Override
        public String toString() {
            return "QuestionEvent[protocolVersion=" + protocolVersion + ", number=" + number
                    + ", total=" + total + ", followUp=" + followUp + ", promptLength=" + prompt.length() + "]";
        }
    }

    record EvaluationEvent(int protocolVersion, String verdict, int score, String feedback,
            List<String> understoodConcepts, List<String> missingConcepts) implements BridgeEvent {
        public EvaluationEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            verdict = BridgeProtocol.requireText(verdict, "verdict", 32);
            BridgeProtocol.requireRange(score, 0, 100, "score");
            feedback = BridgeProtocol.requireText(feedback, "feedback", 16_384);
            understoodConcepts = BridgeProtocol.copyStrings(understoodConcepts, "understoodConcepts", 32, 2048);
            missingConcepts = BridgeProtocol.copyStrings(missingConcepts, "missingConcepts", 32, 2048);
        }

        @Override
        public String toString() {
            return "EvaluationEvent[protocolVersion=" + protocolVersion + ", verdict=" + verdict
                    + ", score=" + score + ", feedbackLength=" + feedback.length()
                    + ", understoodCount=" + understoodConcepts.size()
                    + ", missingCount=" + missingConcepts.size() + "]";
        }
    }

    record QuestionScoreEvent(int protocolVersion, int questionNumber, int score) implements BridgeEvent {
        public QuestionScoreEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            BridgeProtocol.requireRange(questionNumber, 1, 3, "questionNumber");
            BridgeProtocol.requireRange(score, 0, 100, "score");
        }
    }

    record SummaryEvent(int protocolVersion, List<Integer> questionScores, int overallScore, String readiness)
            implements BridgeEvent {
        public SummaryEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            if (questionScores == null || questionScores.size() != 3 || questionScores.stream().anyMatch(score ->
                    score == null || score < 0 || score > 100)) {
                throw new IllegalArgumentException("questionScores must contain three scores");
            }
            questionScores = List.copyOf(questionScores);
            BridgeProtocol.requireRange(overallScore, 0, 100, "overallScore");
            readiness = BridgeProtocol.requireText(readiness, "readiness", 64);
        }
    }

    record PassportSavedEvent(int protocolVersion, String path, String status, String shortFingerprint)
            implements BridgeEvent {
        public PassportSavedEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            path = BridgeProtocol.requireText(path, "path", 4096);
            status = BridgeProtocol.requireText(status, "status", 32);
            shortFingerprint = BridgeProtocol.requireText(shortFingerprint, "shortFingerprint", 64);
        }
    }

    record ProvenanceEvent(int protocolVersion, String status, String disclaimer) implements BridgeEvent {
        public ProvenanceEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            status = BridgeProtocol.requireText(status, "status", 64);
            disclaimer = BridgeProtocol.requireText(disclaimer, "disclaimer", 512);
        }
    }

    record CompletedEvent(int protocolVersion, int exitCode, boolean codexInvoked) implements BridgeEvent {
        public CompletedEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            BridgeProtocol.requireRange(exitCode, 0, 255, "exitCode");
        }
    }

    record ErrorEvent(int protocolVersion, String code, String message, int exitCode) implements BridgeEvent {
        public ErrorEvent {
            BridgeProtocol.requireVersion(protocolVersion);
            code = BridgeProtocol.requireText(code, "code", 64);
            message = BridgeProtocol.requireText(message, "message", 1024);
            BridgeProtocol.requireRange(exitCode, 1, 255, "exitCode");
        }
    }
}
