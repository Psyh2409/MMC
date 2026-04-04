package org.mental_management_center.mmc.service;

import org.springframework.stereotype.Service;
import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.repository.RequestRepository;


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

