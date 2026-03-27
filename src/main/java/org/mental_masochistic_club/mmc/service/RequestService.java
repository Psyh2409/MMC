package org.mental_masochistic_club.mmc.service;

import org.springframework.stereotype.Service;
import org.mental_masochistic_club.mmc.model.Request;
import org.mental_masochistic_club.mmc.repository.RequestRepository;


@Service
public class RequestService {
     private final  RequestRepository repository;

     public RequestService(RequestRepository repository){
         this.repository = repository;
     }

     public void save(Request request) {
         repository.save(request);
     }
}
//public class RequestService {
//}
