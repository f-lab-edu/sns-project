package com.ht.project.snsproject.model.feed;

import com.ht.project.snsproject.enumeration.PublicScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.sql.Timestamp;

@Builder
@Value
@AllArgsConstructor
public class FeedInfo {

    int id;

    String userId;

    String title;

    String content;

    Timestamp date;

    PublicScope publicScope;

    String path;

    String fileNames;
}
