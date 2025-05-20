package com.chatservice.exportroom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ROOMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportRoomEntity {

	@Id
    @Column(name = "ROOMNUMBER", nullable = false)
    private int roomNumber;

    @Column(name = "ROOMTITLE", nullable = false, length = 255)
    private String roomTitle;

    @Column(name = "CURRENTPEOPLE", nullable = false)
    private int currentPeople;

    @Column(name = "MAXPEOPLE", nullable = false)
    private int maxPeople;
}
