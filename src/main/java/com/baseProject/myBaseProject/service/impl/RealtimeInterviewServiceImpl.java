package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.RealtimeProperties;
import com.baseProject.myBaseProject.dto.realtime.CreateRealtimeSessionRequest;
import com.baseProject.myBaseProject.dto.realtime.CreateRealtimeResumeRequest;
import com.baseProject.myBaseProject.dto.realtime.DisconnectRealtimeConnectionRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeAudioFormatResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeConnectionResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventBatchRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventBatchResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeSessionGrantResponse;
import com.baseProject.myBaseProject.entity.InterviewRealtimeEvent;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.entity.InterviewVoiceConnection;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnInputMode;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.RealtimeEventType;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.realtime.RealtimeInterviewInstructionFactory;
import com.baseProject.myBaseProject.realtime.RealtimeInterviewProvider;
import com.baseProject.myBaseProject.realtime.RealtimeProviderRegistry;
import com.baseProject.myBaseProject.realtime.RealtimeSessionGrant;
import com.baseProject.myBaseProject.realtime.RealtimeSessionSpec;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewRealtimeEventRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import com.baseProject.myBaseProject.repository.InterviewVoiceConnectionRepository;
import com.baseProject.myBaseProject.service.InterviewConversationService;
import com.baseProject.myBaseProject.service.RealtimeInterviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RealtimeInterviewServiceImpl implements RealtimeInterviewService {
    private static final String PCM_MIME_TYPE = "audio/pcm";

    private final InterviewSessionRepository sessions;
    private final InterviewFocusAreaRepository focusAreas;
    private final InterviewVoiceConnectionRepository connections;
    private final InterviewRealtimeEventRepository events;
    private final InterviewTurnRepository turns;
    private final InterviewConversationService conversationService;
    private final RealtimeProviderRegistry providers;
    private final RealtimeInterviewInstructionFactory instructionFactory;
    private final RealtimeProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RealtimeInterviewServiceImpl(
            InterviewSessionRepository sessions,
            InterviewFocusAreaRepository focusAreas,
            InterviewVoiceConnectionRepository connections,
            InterviewRealtimeEventRepository events,
            InterviewTurnRepository turns,
            InterviewConversationService conversationService,
            RealtimeProviderRegistry providers,
            RealtimeInterviewInstructionFactory instructionFactory,
            RealtimeProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.focusAreas = focusAreas;
        this.connections = connections;
        this.events = events;
        this.turns = turns;
        this.conversationService = conversationService;
        this.providers = providers;
        this.instructionFactory = instructionFactory;
        this.properties = properties;
        this.clock = clock;
        transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public RealtimeSessionGrantResponse createGrant(
            Long userId,
            Long sessionId,
            CreateRealtimeSessionRequest request) {
        if (!properties.enabled()) {
            throw new DomainException(ErrorCode.REALTIME_NOT_ENABLED);
        }
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        requireAvailable(session);

        RealtimeInterviewProvider provider = providers.provider(properties.provider());
        String voiceName = request.voiceName() == null || request.voiceName().isBlank()
                ? provider.defaultVoice()
                : request.voiceName().strip();
        String instruction = instructionFactory.create(
                session, focusAreas.findBySessionIdOrderByDisplayOrderAsc(sessionId));
        RealtimeSessionGrant grant = provider.createSession(
                specification(session, voiceName, instruction));
        Instant now = clock.instant();
        Long connectionId = transactions.execute(status -> persistGrant(
                userId, sessionId, request.clientPlatform(), grant, now));
        if (connectionId == null) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
        return toResponse(connectionId, grant);
    }

    @Override
    public RealtimeEventBatchResponse recordEvents(
            Long userId,
            Long sessionId,
            Long connectionId,
            RealtimeEventBatchRequest request) {
        RealtimeEventBatchResponse response = transactions.execute(status ->
                persistEvents(userId, sessionId, connectionId, request.events()));
        if (response == null) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
        return response;
    }

    @Override
    public RealtimeSessionGrantResponse resumeGrant(
            Long userId,
            Long sessionId,
            Long connectionId,
            CreateRealtimeResumeRequest request) {
        if (!properties.enabled()) {
            throw new DomainException(ErrorCode.REALTIME_NOT_ENABLED);
        }
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        requireInProgressRealtime(session);
        InterviewVoiceConnection previous = connections
                .findByIdAndSessionId(connectionId, sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.REALTIME_CONNECTION_NOT_FOUND));
        RealtimeInterviewProvider provider = providers.provider(previous.getProvider());
        if (!provider.capabilities().sessionResumption()) {
            throw new DomainException(ErrorCode.REALTIME_RESUMPTION_NOT_AVAILABLE);
        }

        String handle = request.resumptionHandle().strip();
        String instruction = instructionFactory.create(
                session, focusAreas.findBySessionIdOrderByDisplayOrderAsc(sessionId));
        RealtimeSessionGrant grant = provider.resumeSession(
                specification(session, previous.getVoiceName(), instruction), handle);
        Instant now = clock.instant();
        Long resumedConnectionId = transactions.execute(status -> persistResumeGrant(
                userId, sessionId, connectionId, request.clientPlatform(), handle, grant, now));
        if (resumedConnectionId == null) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
        return toResponse(resumedConnectionId, grant);
    }

    @Override
    public RealtimeConnectionResponse disconnect(
            Long userId,
            Long sessionId,
            Long connectionId,
            DisconnectRealtimeConnectionRequest request) {
        RealtimeConnectionResponse response = transactions.execute(status -> {
            InterviewSession session = sessions.findOwnedByIdForUpdate(sessionId, userId)
                    .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            InterviewVoiceConnection connection = connections
                    .findByIdAndSessionId(connectionId, sessionId)
                    .orElseThrow(() -> new DomainException(ErrorCode.REALTIME_CONNECTION_NOT_FOUND));
            requireLatencyOrder(request.p50LatencyMs(), request.p95LatencyMs());
            Instant now = clock.instant();
            connection.markDisconnected(
                    normalizeReason(request.reason()),
                    request.fallbackToTurnBased(),
                    request.p50LatencyMs(),
                    request.p95LatencyMs(),
                    now);
            if (request.fallbackToTurnBased()
                    && session.getMode() == InterviewSessionMode.VOICE_REALTIME) {
                session.fallbackToTurnBased(now);
            }
            return toConnectionResponse(session, connection);
        });
        if (response == null) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
        if (request.fallbackToTurnBased()) {
            conversationService.continueAfterRealtimeFallback(userId, sessionId);
        }
        return response;
    }

    private Long persistGrant(
            Long userId,
            Long sessionId,
            String clientPlatform,
            RealtimeSessionGrant grant,
            Instant now) {
        InterviewSession session = sessions.findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        requireAvailable(session);
        session.configureRealtime(grant.provider(), grant.voiceName(), now);
        InterviewVoiceConnection connection = connections.save(
                InterviewVoiceConnection.builder()
                        .session(session)
                        .provider(grant.provider())
                        .transport(grant.transport())
                        .modelName(grant.modelName())
                        .voiceName(grant.voiceName())
                        .externalSessionId(grant.externalSessionId())
                        .clientPlatform(normalize(clientPlatform))
                        .inputSampleRate(grant.inputSampleRate())
                        .outputSampleRate(grant.outputSampleRate())
                        .createdAt(now)
                        .build());
        return connection.getId();
    }

    private Long persistResumeGrant(
            Long userId,
            Long sessionId,
            Long previousConnectionId,
            String clientPlatform,
            String resumptionHandle,
            RealtimeSessionGrant grant,
            Instant now) {
        InterviewSession session = sessions.findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        requireInProgressRealtime(session);
        InterviewVoiceConnection previous = connections
                .findByIdAndSessionId(previousConnectionId, sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.REALTIME_CONNECTION_NOT_FOUND));
        previous.updateResumptionHandle(resumptionHandle);
        if (previous.getDisconnectedAt() == null) {
            previous.markDisconnected("SESSION_RESUMED", false, null, null, now);
        }
        InterviewVoiceConnection resumed = connections.save(InterviewVoiceConnection.builder()
                .session(session)
                .provider(grant.provider())
                .transport(grant.transport())
                .modelName(grant.modelName())
                .voiceName(grant.voiceName())
                .externalSessionId(grant.externalSessionId())
                .clientPlatform(normalize(clientPlatform))
                .inputSampleRate(grant.inputSampleRate())
                .outputSampleRate(grant.outputSampleRate())
                .createdAt(now)
                .build());
        return resumed.getId();
    }

    private RealtimeEventBatchResponse persistEvents(
            Long userId,
            Long sessionId,
            Long connectionId,
            List<RealtimeEventRequest> requestedEvents) {
        InterviewSession session = sessions.findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_IN_PROGRESS);
        }
        InterviewVoiceConnection connection = connections
                .findByIdAndSessionId(connectionId, sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.REALTIME_CONNECTION_NOT_FOUND));

        List<RealtimeEventRequest> ordered = requestedEvents.stream()
                .sorted(Comparator.comparingLong(RealtimeEventRequest::sequenceNumber))
                .toList();
        Map<String, Long> batchProviderIds = new HashMap<>();
        Map<Long, String> batchSequences = new HashMap<>();
        int accepted = 0;
        int duplicates = 0;
        int createdTurns = 0;
        for (RealtimeEventRequest request : ordered) {
            String providerEventId = request.providerEventId().strip();
            Long priorSequence = batchProviderIds.putIfAbsent(
                    providerEventId, request.sequenceNumber());
            if (priorSequence != null) {
                if (priorSequence != request.sequenceNumber()) {
                    throw new DomainException(ErrorCode.REALTIME_EVENT_INVALID);
                }
                duplicates++;
                continue;
            }
            String priorProviderId = batchSequences.putIfAbsent(
                    request.sequenceNumber(), providerEventId);
            if (priorProviderId != null && !priorProviderId.equals(providerEventId)) {
                throw new DomainException(ErrorCode.REALTIME_EVENT_SEQUENCE_CONFLICT);
            }
            if (events.findByConnectionIdAndProviderEventId(connectionId, providerEventId)
                    .isPresent()) {
                duplicates++;
                continue;
            }
            if (events.findByConnectionIdAndSequenceNumber(
                    connectionId, request.sequenceNumber()).isPresent()) {
                throw new DomainException(ErrorCode.REALTIME_EVENT_SEQUENCE_CONFLICT);
            }

            requireEventPayload(request);
            Instant now = clock.instant();
            InterviewRealtimeEvent event = InterviewRealtimeEvent.builder()
                    .connection(connection)
                    .providerEventId(providerEventId)
                    .sequenceNumber(request.sequenceNumber())
                    .eventType(request.eventType())
                    .transcriptText(normalize(request.transcriptText()))
                    .detail(normalize(request.detail()))
                    .latencyMs(request.latencyMs())
                    .occurredAt(request.occurredAt())
                    .createdAt(now)
                    .build();
            events.save(event);
            createdTurns += applyEvent(session, connection, request, now);
            event.markProcessed(now);
            accepted++;
        }
        return new RealtimeEventBatchResponse(
                connectionId, accepted, duplicates, createdTurns,
                session.getCurrentTurnIndex());
    }

    private int applyEvent(
            InterviewSession session,
            InterviewVoiceConnection connection,
            RealtimeEventRequest event,
            Instant now) {
        return switch (event.eventType()) {
            case SESSION_CONNECTED -> {
                if (connection.getConnectedAt() == null) {
                    connection.markConnected(normalize(event.detail()), now);
                }
                yield 0;
            }
            case SESSION_RESUMPTION_UPDATED -> {
                connection.updateResumptionHandle(event.detail().strip());
                yield 0;
            }
            case USER_TRANSCRIPT_FINAL -> createTurn(
                    session, InterviewTurnRole.CANDIDATE, event, now);
            case ASSISTANT_TRANSCRIPT_FINAL -> createTurn(
                    session, InterviewTurnRole.INTERVIEWER, event, now);
            case ASSISTANT_INTERRUPTED -> {
                turns.findFirstBySessionIdAndRoleOrderByTurnIndexDesc(
                                session.getId(), InterviewTurnRole.INTERVIEWER)
                        .ifPresent(InterviewTurn::markInterrupted);
                yield 0;
            }
            case SESSION_DISCONNECTED -> {
                if (connection.getDisconnectedAt() == null) {
                    connection.markDisconnected(
                            normalizeReason(event.detail()), false, null, null, now);
                }
                yield 0;
            }
            default -> 0;
        };
    }

    private int createTurn(
            InterviewSession session,
            InterviewTurnRole role,
            RealtimeEventRequest event,
            Instant now) {
        InterviewTurn current = turns
                .findBySessionIdAndTurnIndex(session.getId(), session.getCurrentTurnIndex())
                .orElse(null);
        if (role == InterviewTurnRole.INTERVIEWER
                && current != null
                && current.getRole() == InterviewTurnRole.INTERVIEWER) {
            if (event.latencyMs() != null && current.getLatencyMs() == null) {
                current.recordLatency(event.latencyMs());
            }
            return 0;
        }

        int turnIndex = session.getCurrentTurnIndex() + 1;
        InterviewTurn turn = InterviewTurn.builder()
                .session(session)
                .replyToTurn(role == InterviewTurnRole.INTERVIEWER ? current : null)
                .turnIndex(turnIndex)
                .role(role)
                .inputMode(InterviewTurnInputMode.VOICE_REALTIME)
                .contentText(event.transcriptText().strip())
                .action(role == InterviewTurnRole.INTERVIEWER
                        ? InterviewTurnAction.FOLLOW_UP
                        : null)
                .processingStatus(role == InterviewTurnRole.CANDIDATE
                        ? InterviewTurnProcessingStatus.COMPLETED
                        : null)
                .latencyMs(event.latencyMs())
                .createdAt(now)
                .build();
        turns.save(turn);
        session.recordTurn(turnIndex, null, now);
        return 1;
    }

    private void requireEventPayload(RealtimeEventRequest event) {
        boolean transcriptRequired = event.eventType() == RealtimeEventType.USER_TRANSCRIPT_FINAL
                || event.eventType() == RealtimeEventType.ASSISTANT_TRANSCRIPT_FINAL;
        if (transcriptRequired
                && (event.transcriptText() == null || event.transcriptText().isBlank())) {
            throw new DomainException(ErrorCode.REALTIME_EVENT_INVALID);
        }
        if (event.eventType() == RealtimeEventType.SESSION_RESUMPTION_UPDATED
                && (event.detail() == null || event.detail().isBlank())) {
            throw new DomainException(ErrorCode.REALTIME_EVENT_INVALID);
        }
    }

    private RealtimeSessionSpec specification(
            InterviewSession session,
            String voiceName,
            String instruction) {
        return new RealtimeSessionSpec(
                session.getId(),
                session.getLanguageCode(),
                grantDurationMinutes(session),
                session.getInterviewerStyle(),
                voiceName,
                instruction);
    }

    private int grantDurationMinutes(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
                || session.getDeadlineAt() == null) {
            return session.getDurationMinutes();
        }
        long remainingSeconds = Duration.between(
                clock.instant(), session.getDeadlineAt()).getSeconds();
        if (remainingSeconds <= 0) {
            throw new DomainException(ErrorCode.REALTIME_SESSION_NOT_AVAILABLE);
        }
        long roundedMinutes = (remainingSeconds + 59) / 60;
        return (int) Math.min(session.getDurationMinutes(), roundedMinutes);
    }

    private void requireInProgressRealtime(InterviewSession session) {
        if (session.getMode() != InterviewSessionMode.VOICE_REALTIME
                || session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new DomainException(ErrorCode.REALTIME_SESSION_NOT_AVAILABLE);
        }
    }

    private void requireLatencyOrder(Integer p50LatencyMs, Integer p95LatencyMs) {
        if (p50LatencyMs != null && p95LatencyMs != null && p50LatencyMs > p95LatencyMs) {
            throw new DomainException(
                    ErrorCode.REALTIME_EVENT_INVALID,
                    "p50LatencyMs must not exceed p95LatencyMs");
        }
    }

    private RealtimeConnectionResponse toConnectionResponse(
            InterviewSession session,
            InterviewVoiceConnection connection) {
        return new RealtimeConnectionResponse(
                connection.getId(),
                session.getMode(),
                connection.getConnectedAt(),
                connection.getDisconnectedAt(),
                connection.getDisconnectReason(),
                connection.isFellBackToTurnBased(),
                connection.getP50LatencyMs(),
                connection.getP95LatencyMs());
    }

    private void requireAvailable(InterviewSession session) {
        boolean ready = session.getStatus() == InterviewSessionStatus.READY
                || session.getStatus() == InterviewSessionStatus.IN_PROGRESS;
        if (session.getMode() != InterviewSessionMode.VOICE_REALTIME || !ready) {
            throw new DomainException(ErrorCode.REALTIME_SESSION_NOT_AVAILABLE);
        }
    }

    private RealtimeSessionGrantResponse toResponse(
            Long connectionId,
            RealtimeSessionGrant grant) {
        return new RealtimeSessionGrantResponse(
                connectionId,
                grant.provider(),
                grant.transport(),
                grant.endpoint(),
                grant.ephemeralToken(),
                grant.modelName(),
                grant.voiceName(),
                audioFormat(grant.inputSampleRate()),
                audioFormat(grant.outputSampleRate()),
                grant.expiresAt(),
                grant.sessionSetup());
    }

    private RealtimeAudioFormatResponse audioFormat(int sampleRate) {
        return new RealtimeAudioFormatResponse(
                PCM_MIME_TYPE + ";rate=" + sampleRate,
                sampleRate,
                16,
                1);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String normalizeReason(String value) {
        String normalized = normalize(value);
        return normalized == null ? "CLIENT_DISCONNECTED" : normalized;
    }
}
