package org.mental_masochistic_club.mmc.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String contact;
    @Getter
    @Setter
    private String message;
//    @Column(columnDefinition = "TEXT")
//    private String content;
    @Getter
    @Setter
    private LocalDateTime createdAt;

    private

    @PrePersist
    void prePersist(){
        createdAt = LocalDateTime.now();
    }

    public Request(){}


}
