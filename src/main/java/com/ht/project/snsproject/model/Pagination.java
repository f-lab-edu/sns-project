package com.ht.project.snsproject.model;

import lombok.Value;

@Value
public class Pagination {

    private Integer cursor;
    private int listSize = 3;//2개씩 불러온다.

}
