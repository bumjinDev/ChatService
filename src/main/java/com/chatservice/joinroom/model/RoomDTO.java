package com.chatservice.joinroom.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class RoomDTO {

    private int roomNumber;
    private String roomTitle;
    private int currentPeople;
    private int maxPeople;
}
