package com.example.coconote.api.thread.tag.service;

import com.example.coconote.api.channel.channel.entity.Channel;
import com.example.coconote.api.channel.channel.repository.ChannelRepository;
import com.example.coconote.api.member.entity.Member;
import com.example.coconote.api.search.dto.EntityType;
import com.example.coconote.api.search.dto.IndexEntityMessage;
import com.example.coconote.api.search.entity.ThreadDocument;
import com.example.coconote.api.search.mapper.ThreadMapper;
import com.example.coconote.api.thread.tag.dto.request.TagCreateReqDto;
import com.example.coconote.api.thread.tag.dto.request.TagSearchReqListDto;
import com.example.coconote.api.thread.tag.dto.request.TagUpdateReqDto;
import com.example.coconote.api.thread.tag.dto.response.TagResDto;
import com.example.coconote.api.thread.tag.dto.response.TagSearchListResDto;
import com.example.coconote.api.thread.tag.entity.Tag;
import com.example.coconote.api.thread.tag.repository.TagRepository;
import com.example.coconote.api.thread.thread.dto.requset.ThreadReqDto;
import com.example.coconote.api.thread.thread.dto.response.ThreadResDto;
import com.example.coconote.api.thread.thread.entity.MessageType;
import com.example.coconote.api.thread.thread.entity.Thread;
import com.example.coconote.api.thread.thread.repository.ThreadRepository;
import com.example.coconote.api.thread.threadFile.dto.request.ThreadFileDto;
import com.example.coconote.api.thread.threadTag.entity.ThreadTag;
import com.example.coconote.api.thread.threadTag.repository.ThreadTagRepository;
import com.example.coconote.api.workspace.workspaceMember.entity.WorkspaceMember;
import com.example.coconote.common.IsDeleted;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final ChannelRepository channelRepository;
    private final ThreadRepository threadRepository;
    private final ThreadTagRepository threadTagRepository;
    private final ThreadMapper threadMapper;
    private final KafkaTemplate kafkaTemplate;


    public Tag createTag(TagCreateReqDto dto) {
        Channel channel = channelRepository.findById(dto.getChannelId()).orElseThrow(()->new EntityNotFoundException("Channel not found"));
        Tag tag = tagRepository.save(dto.toEntity(channel));
        return tag;
    }

    public ThreadResDto createAndAddTag(ThreadReqDto dto) {
        Tag tag;
        MessageType messageType;
        if(dto.getTagId()==null){
            messageType = MessageType.CREATE_AND_ADD_TAG;
            Channel channel = channelRepository.findById(dto.getChannelId()).orElseThrow(()->new EntityNotFoundException("Channel not found"));
            tag = tagRepository.save(Tag.builder().name(dto.getTagName()).color(dto.getTagColor()).channel(channel).build());
        } else {
            messageType = MessageType.ADD_TAG;
            tag = tagRepository.findById(dto.getTagId()).orElseThrow(()->new EntityNotFoundException("Tag not found"));
        }
        Thread thread = threadRepository.findById(dto.getThreadId()).orElseThrow(()->new EntityNotFoundException("Thread not found"));
        ThreadTag threadTag = threadTagRepository.save(new ThreadTag(thread, tag));

        ThreadDocument document = threadMapper.toDocument(thread, thread.getWorkspaceMember().getProfileImage());  // toDocument로 미리 변환
        IndexEntityMessage<ThreadDocument> indexEntityMessage = new IndexEntityMessage<>(thread.getChannel().getSection().getWorkspace().getWorkspaceId(), EntityType.THREAD, document);
        kafkaTemplate.send("thread_entity_search", indexEntityMessage.toJson());

        return ThreadResDto.builder()
                .type(messageType)
                .threadTagId(threadTag.getId())
                .id(thread.getId())
                .tagId(tag.getId())
                .tagName(tag.getName())
                .tagColor(tag.getColor())
                .parentThreadId(thread.getParent() != null ? thread.getParent().getId() : null)
                .build();
    }

    public List<TagResDto> tagList(Long channelId) {
        Channel channel = channelRepository.findById(channelId).orElseThrow(()->new EntityNotFoundException("Channel not found"));
        List<Tag> tags = tagRepository.findAllByChannelAndIsDeleted(channel, IsDeleted.N);
        List<TagResDto> tagResDtos = tags.stream().map(tag -> tag.fromEntity()).toList();
        return tagResDtos;
    }

    public Tag updateTag(TagUpdateReqDto dto) {
        Tag tag = tagRepository.findById(dto.getTagId()).orElseThrow(()->new EntityNotFoundException("Tag not found"));
        tag.updateName(dto.getUpdateTagName());
        return tag;
    }

    public void deleteTag(Long tagId) {
        Tag tag = tagRepository.findById(tagId).orElseThrow(()->new EntityNotFoundException("Tag not found"));
        tag.deleteTag();
    }


    @Transactional
    public List<TagSearchListResDto> searchTag(Long channelId, List<Long> tagSearchIds) {
// 입력된 태그의 개수
        Long tagCount = (long) tagSearchIds.size();

        // 모든 태그를 만족하는 쓰레드를 찾습니다.
        List<Thread> threads = threadTagRepository.findThreadsByChannelAndAllTagIds(channelId, tagSearchIds, tagCount);

        // DTO로 변환
        return threads.stream()
                .map(thread -> {
                    WorkspaceMember workspaceMember = thread.getWorkspaceMember();

                    // 태그 정보 변환
                    List<TagResDto> tagResDtos = thread.getThreadTags().stream()
                            .map(tTag -> TagResDto.builder()
                                    .id(tTag.getTag().getId())
                                    .name(tTag.getTag().getName())
                                    .color(tTag.getTag().getColor())
                                    .threadTagId(tTag.getId())
                                    .build())
                            .collect(Collectors.toList());

                    // 파일 정보 변환
                    List<ThreadFileDto> fileDtos = thread.getThreadFiles().stream()
                            .map(threadFile -> ThreadFileDto.builder()
                                    .fileId(threadFile.getId())
                                    .fileURL(threadFile.getFileURL())
                                    .fileName(threadFile.getFileName())
                                    .build())
                            .collect(Collectors.toList());

                    // 결과 DTO 생성
                    return TagSearchListResDto.builder()
                            .threadId(thread.getId())
                            .content(thread.getContent())
                            .memberNickName(workspaceMember.getNickname())
                            .profileImageUrl(workspaceMember.getProfileImage())
                            .channelId(thread.getChannel().getChannelId())
                            .createdTime(thread.getCreatedTime().toString())
                            .tags(tagResDtos)
                            .fileUrls(fileDtos)
                            .parentThreadId(thread.getParent() != null ? thread.getParent().getId() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
