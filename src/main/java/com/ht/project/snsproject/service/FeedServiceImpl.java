package com.ht.project.snsproject.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ht.project.snsproject.Exception.InvalidApproachException;
import com.ht.project.snsproject.enumeration.FriendStatus;
import com.ht.project.snsproject.enumeration.PublicScope;
import com.ht.project.snsproject.mapper.FeedMapper;
import com.ht.project.snsproject.mapper.FriendMapper;
import com.ht.project.snsproject.model.Pagination;
import com.ht.project.snsproject.model.feed.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

@Service
public class FeedServiceImpl implements FeedService{

    @Autowired
    FeedMapper feedMapper;

    @Autowired
    FriendMapper friendMapper;

    @Autowired
    @Qualifier("awsFileService")
    FileService fileService;

    @Autowired
    RecommendService recommendService;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Transactional
    @Override
    public void feedUpload(List<MultipartFile> files, FeedVO feedVo, String userId) {

        Timestamp date = Timestamp.valueOf(LocalDateTime.now());
        FeedInsert feedInsert = FeedInsert.builder()
                .userId(userId)
                .title(feedVo.getTitle())
                .content(feedVo.getContent())
                .date(date)
                .publicScope(feedVo.getPublicScope())
                .recommend(0)
                .build();
        feedMapper.feedUpload(feedInsert);
        if(!files.isEmpty()) {
            fileService.fileUpload(files, userId, feedInsert.getId());
        }
    }

    @Transactional
    @Override
    public Feed getFeed(String userId, String targetId, int id) {

        Feed.FeedBuilder feedBuilder =Feed.builder();
        FeedInfo feedInfo = getFeedInfo(id, userId, targetId);

        String fileNames = feedInfo.getFileNames();
        int feedId = feedInfo.getId();
        int recommend = recommendService.getRecommend(id);

        feedBuilder.id(feedId)
                .userId(feedInfo.getUserId())
                .title(feedInfo.getTitle())
                .content(feedInfo.getContent())
                .date(feedInfo.getDate())
                .publicScope(feedInfo.getPublicScope())
                .recommend(recommend);

        if(fileNames!=null) {
            String filePath = feedInfo.getPath();
            String[] fileNameArray = fileNames.split(",");
            List<FileVo> fileVoList = new ArrayList<>();
            int fileIndex = 0;
            for(String fileName:fileNameArray){
                fileVoList.add(FileVo.getInstance(++fileIndex, filePath, fileName));
            }
            feedBuilder.files(fileVoList);
        }

        return feedBuilder.build();
    }

    public FeedInfo getFeedInfoCache(int feedId){

        String key = "feedInfo:"+feedId;
        Map<?,?> cache = (Map<?, ?>) redisTemplate.opsForValue().get(key);

        if(cache!=null){

            return FeedInfo.builder().id((Integer) cache.get("id"))
                    .userId((String) cache.get("userId"))
                    .title((String) cache.get("title"))
                    .content((String) cache.get("content"))
                    .date(new Timestamp((Long) cache.get("date")))
                    .publicScope(PublicScope.valueOf((String) cache.get("publicScope")))
                    .path((String) cache.get("path"))
                    .fileNames((String) cache.get("fileNames"))
                    .build();

        }


        FeedInfo feedInfo = feedMapper.getFeedInfoCache(feedId);
        redisTemplate.opsForValue().set(key, feedInfo);

        return feedInfo;
    }

    public FeedInfo getFeedInfo(int feedId, String userId, String targetId){

        String key = "feedInfo:"+feedId;
        Map<?,?> cache = (Map<?, ?>) redisTemplate.opsForValue().get(key);

        if(cache!=null){

            return FeedInfo.builder().id((Integer) cache.get("id"))
                    .userId((String) cache.get("userId"))
                    .title((String) cache.get("title"))
                    .content((String) cache.get("content"))
                    .date(new Timestamp((Long) cache.get("date")))
                    .publicScope(PublicScope.valueOf((String) cache.get("publicScope")))
                    .path((String) cache.get("path"))
                    .fileNames((String) cache.get("fileNames"))
                    .build();

        }

        FeedInfo feedInfo;

        if(userId.equals(targetId)){
            feedInfo = feedMapper.getFeed(FeedParam.create(feedId, targetId, FriendStatus.ME));
        }else {

            FriendStatus friendStatus = friendMapper.getFriendRelationStatus(userId, targetId).getFriendStatus();

            if (friendStatus == FriendStatus.BLOCK) {
                throw new InvalidApproachException("유효하지 않은 접근입니다.");
            }

            feedInfo = feedMapper.getFeed(FeedParam.create(feedId, targetId, friendStatus));

            if (feedInfo == null) {
                throw new InvalidApproachException("일치하는 데이터가 없습니다.");
            }
        }

        redisTemplate.opsForValue().set(key, feedInfo);

        return feedInfo;
    }

    /*  getFeedList() 메소드의 경우,
        targetId 에 해당하는 user 의 피드 목록을 조회해야하므로 순서와 데이터 정확도가 중요하기 때문에
        피드 전체를 캐시에서 확인하지 않고,
        recommend 수 증감은 redis 에서 저장하므로 캐시에서는 recommend 수만 체크하여 가져온다.
     */
    @Transactional
    @Override
    public List<Feed> getFeedList(String userId, String targetId, Pagination pagination) {

        if(userId.equals(targetId)){
            return getFeeds(FeedListParam.create(targetId, pagination, PublicScope.ME));
        }

        FriendStatus friendStatus = friendMapper.getFriendRelationStatus(userId, targetId).getFriendStatus();

        switch (friendStatus){
            case FRIEND:
                return getFeeds(FeedListParam.create(targetId, pagination, PublicScope.FRIENDS));

            case BLOCK:
                throw new InvalidApproachException("유효하지 않은 접근입니다.");

            default:
                return getFeeds(FeedListParam.create(targetId, pagination, PublicScope.ALL));
        }

    }

    @Transactional
    @Override
    public List<Feed> getFeeds(FeedListParam feedListParam) {

        List<FeedInfo> feedInfoList = feedMapper.getFeedList(feedListParam);
        List<Feed> feeds = new ArrayList<>();

        for(FeedInfo feedInfo:feedInfoList){

            List<FileVo> files = new ArrayList<>();
            int feedId = feedInfo.getId();
            int recommend = recommendService.getRecommend(feedId);

            Feed.FeedBuilder builder = Feed.builder()
                    .id(feedId)
                    .userId(feedInfo.getUserId())
                    .title(feedInfo.getTitle())
                    .content(feedInfo.getContent())
                    .date(feedInfo.getDate())
                    .publicScope(feedInfo.getPublicScope())
                    .recommend(recommend);

            int fileIndex = 0;
            if(feedInfo.getFileNames()!=null) {
                StringTokenizer st = new StringTokenizer(feedInfo.getFileNames(), ",");
                while (st.hasMoreTokens()) {
                    FileVo tmpFile = FileVo.getInstance(++fileIndex, feedInfo.getPath(), st.nextToken());
                    files.add(tmpFile);
                }
                builder.files(files);
            }
            Feed tmp = builder.build();
            feeds.add(tmp);
        }
        return feeds;
    }

    @Override
    public List<Feed> getFriendsFeedList(String userId, Pagination pagination) {
        List<FeedInfo> feedInfoList = feedMapper.getFriendsFeedList(FriendsFeedList.create(userId,pagination));
        List<Feed> feeds= new ArrayList<>();
        for (FeedInfo feedInfo:feedInfoList) {

            List<FileVo> files = new ArrayList<>();
            StringTokenizer st = new StringTokenizer(feedInfo.getFileNames(),",");
            int fileIndex = 0;
            int feedId = feedInfo.getId();
            int recommend = recommendService.getRecommend(feedId);

            Feed.FeedBuilder builder = Feed.builder()
                    .id(feedId)
                    .userId(feedInfo.getUserId())
                    .title(feedInfo.getTitle())
                    .content(feedInfo.getContent())
                    .date(feedInfo.getDate())
                    .publicScope(feedInfo.getPublicScope())
                    .recommend(recommend);

            while(st.hasMoreTokens()){
                FileVo tmpFile = FileVo.getInstance(++fileIndex,feedInfo.getPath(),st.nextToken());
                files.add(tmpFile);
            }
            builder.files(files);
            Feed tmp = builder.build();
            feeds.add(tmp);
        }

        return feeds;
    }


    @Transactional
    @Override
    public void deleteFeed(int id, String userId) {
        boolean result = feedMapper.deleteFeed(new FeedDeleteParam(id, userId));

        if(!result){
            throw new InvalidApproachException("일치하는 데이터가 없습니다.");
        }
        fileService.deleteAllFiles(id);
    }

    @Transactional
    @Override
    public void updateFeed(List<MultipartFile> files, FeedUpdateParam feedUpdateParam, int feedId, String userId) {

        Timestamp date = Timestamp.valueOf(LocalDateTime.now());
        FeedUpdate feedUpdate = FeedUpdate.builder()
                .id(feedId)
                .userId(userId)
                .title(feedUpdateParam.getTitle())
                .content(feedUpdateParam.getContent())
                .date(date)
                .publicScope(feedUpdateParam.getPublicScope())
                .build();

        boolean result = feedMapper.updateFeed(feedUpdate);
        if(!result){
            throw new InvalidApproachException("일치하는 데이터가 없습니다.");
        }
        fileService.updateFiles(files,userId,feedId);
    }
}
