package org.mental_masochistic_club.mentalmasochisticclub.service;

import org.springframework.stereotype.Service;
import org.mental_masochistic_club.mentalmasochisticclub.model.Request;
import org.mental_masochistic_club.mentalmasochisticclub.repository.RequestRepository;


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
