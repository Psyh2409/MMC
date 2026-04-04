package org.mental_management_center.mmc.model;

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
    @Getter
    @Setter
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist(){
        createdAt = LocalDateTime.now();
    }

    public Request(){}


}
