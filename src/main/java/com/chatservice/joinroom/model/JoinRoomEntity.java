package com.chatservice.joinroom.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "ROOMS")
public class JoinRoomEntity {

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
