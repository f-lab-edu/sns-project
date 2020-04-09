package com.ht.project.snsproject.model.feed;

import com.ht.project.snsproject.enumeration.PublicScope;
import java.sql.Timestamp;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class FeedInsert {

  int id;

  String userId;

  String title;

  String content;

  Timestamp date;

  PublicScope publicScope;

  int recommend;
}
