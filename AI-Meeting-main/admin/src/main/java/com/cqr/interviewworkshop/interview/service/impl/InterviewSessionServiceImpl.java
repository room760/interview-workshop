package com.cqr.interviewworkshop.interview.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.Update;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqr.interviewworkshop.agent.application.BusinessAgentResolver;
import com.cqr.interviewworkshop.agent.application.BusinessAgentScene;
import com.cqr.interviewworkshop.common.convention.exception.ClientException;
import com.cqr.interviewworkshop.common.enums.InterviewErrorCodeEnum;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewConversationPageReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewConversationRespDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewSessionCreateRespDTO;
import com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.cqr.interviewworkshop.interview.application.InterviewSessionOwnershipService;
import com.cqr.interviewworkshop.interview.dao.entity.InterviewSession;
import com.cqr.interviewworkshop.interview.dao.repository.InterviewSessionRepository;
import com.cqr.interviewworkshop.interview.service.InterviewSessionService;
import com.cqr.interviewworkshop.interview.service.model.InterviewSessionStatus;
import com.jayway.jsonpath.Criteria;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewSessionOwnershipService ownershipService;
    private final BusinessAgentResolver businessAgentResolver;
    private final ObjectProvider<InterviewSessionRuntimeSnapshotService> runtimeSnapshotServiceProvider;

    @Override
    public InterviewSessionCreateRespDTO createSession(Long userId) {
        abandonActiveSessions(userId);

        InterviewSession session = new InterviewSession();
        session.setSessionId(IdUtil.getSnowflakeNextIdStr());
        session.setUserId(userId);
        session.setStatus(InterviewSessionStatus.DRAFT.name());
        session.setConversationTitle("Interview Session");
        session.setInterviewerAgentId(
                businessAgentResolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_ASKING).getId());
        session.setDelFlag(0);
        interviewSessionRepository.save(session);

        InterviewSessionRuntimeSnapshotService runtimeSnapshotService = runtimeSnapshotServiceProvider.getIfAvailable();
        if (runtimeSnapshotService != null) {
            runtimeSnapshotService.initializeDraftSnapshot(session);
        }
        return new InterviewSessionCreateRespDTO(session.getSessionId(), session.getStatus());
    }

    @Override
    public IPage<InterviewConversationRespDTO> pageConversations(Long userId, InterviewConversationPageReqDTO requestParam) {
        Pageable pageable = PageRequest.of(requestParam.getCurrent() - 1, requestParam.getSize());
        org.springframework.data.domain.Page<InterviewSession> sessionPage = queryPage(userId, requestParam, pageable);
        Page<InterviewConversationRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        result.setTotal(sessionPage.getTotalElements());
        result.setRecords(sessionPage.getContent().stream().map(this::toRespDTO).collect(Collectors.toList()));
        return result;
    }

    @Override
    public InterviewSession getBySessionId(String sessionId) {
        return interviewSessionRepository.findBySessionIdAndDelFlag(sessionId, 0).orElse(null);
    }

    @Override
    public InterviewSession requireOwnedSession(String sessionId, Long userId) {
        return ownershipService.requireOwnedSession(sessionId, userId);
    }

    @Override
    public void markResumeUploading(String sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);

        boolean success = interviewSessionRepository.tryMarkResumeUploading(sessionId,userId);
        if(!success){
            throw new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_INVALID_STATE);
        }
    }

    @Override
    public void markReady(String sessionId, Long userId, String resumeFileUrl, String interviewType) {
        requireOwnedSession(sessionId, userId);

        boolean success = interviewSessionRepository.markReadyIfUploading(
                        sessionId,
                        userId,
                        resumeFileUrl,
                        interviewType
                );
        if (!success) {
            throw new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_INVALID_STATE);
        }
    }

    @Override
    public void markDraft(String sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        interviewSessionRepository.markDraftIfUploading(sessionId, userId);
    }

    @Override
    public void markInProgressIfReady(String sessionId, Long userId) {
        InterviewSession session = requireOwnedSession(sessionId, userId);
        if (InterviewSessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            return;
        }
        if (!InterviewSessionStatus.READY.name().equals(session.getStatus())) {
            throw new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_INVALID_STATE);
        }

        Date startTime = session.getStartTime() == null ? new Date() : session.getStartTime();

        boolean success = interviewSessionRepository.markInProgressIfReady(sessionId, userId, startTime);
        if (!success) {
            InterviewSession latest = requireOwnedSession(sessionId, userId);
            if (InterviewSessionStatus.IN_PROGRESS.name().equals(latest.getStatus())) {
                return;
            }
            throw new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_INVALID_STATE);
        }
    }

    @Override
    public void finishSession(String sessionId, Long userId) {
        InterviewSession session = requireOwnedSession(sessionId, userId);

        if (InterviewSessionStatus.FINISHED.name()
                .equals(session.getStatus())) {
            return;
        }

        Date now = new Date();
        Date startTime = session.getStartTime() == null
                ? (session.getCreateTime() == null ? now : session.getCreateTime())
                : session.getStartTime();

        boolean success = interviewSessionRepository.finishIfCurrentStatus(
                        sessionId,
                        userId,
                        session.getStatus(),
                        startTime,
                        now
                );

        if (!success) {
            InterviewSession latest = requireOwnedSession(sessionId, userId);

            if (InterviewSessionStatus.FINISHED.name().equals(latest.getStatus())) {
                return;
            }

            throw new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_INVALID_STATE
            );
        }
    }

    @Override
    public void abandonActiveSessions(Long userId) {
        Date now = new Date();

        interviewSessionRepository.abandonActiveSessions(
                userId,
                List.of(
                        InterviewSessionStatus.DRAFT.name(),
                        InterviewSessionStatus.RESUME_UPLOADING.name(),
                        InterviewSessionStatus.READY.name(),
                        InterviewSessionStatus.IN_PROGRESS.name()
                ),
                now
        );
    }

    private org.springframework.data.domain.Page<InterviewSession> queryPage(
            Long userId,
            InterviewConversationPageReqDTO requestParam,
            Pageable pageable) {
        if (StrUtil.isNotBlank(requestParam.getKeyword())) {
            String keyword = requestParam.getKeyword().trim();
            if (StrUtil.isNotBlank(requestParam.getStatus())) {
                return interviewSessionRepository.findByUserIdAndStatusAndDelFlagAndTitleContaining(
                        userId,
                        requestParam.getStatus().trim(),
                        0,
                        keyword,
                        pageable
                );
            }
            return interviewSessionRepository.findByUserIdAndDelFlagAndTitleContaining(userId, 0, keyword, pageable);
        }
        if (StrUtil.isNotBlank(requestParam.getStatus())) {
            return interviewSessionRepository.findByUserIdAndStatusAndDelFlagOrderByUpdateTimeDesc(
                    userId,
                    requestParam.getStatus().trim(),
                    0,
                    pageable
            );
        }
        return interviewSessionRepository.findByUserIdAndDelFlagOrderByUpdateTimeDesc(userId, 0, pageable);
    }

    private InterviewConversationRespDTO toRespDTO(InterviewSession session) {
        InterviewConversationRespDTO respDTO = new InterviewConversationRespDTO();
        BeanUtils.copyProperties(session, respDTO);
        return respDTO;
    }
}
