package com.ht.project.snsproject.mapper;

import com.ht.project.snsproject.model.Pagination;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FeedRecommendMapper {

  List<Integer> findFeedIdByLatestOrder(Pagination pagination);
}
