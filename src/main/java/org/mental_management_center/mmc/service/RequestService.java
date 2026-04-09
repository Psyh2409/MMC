package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.RequestRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
@Service
public class RequestService {
     private final RequestRepository repository;
     private final UserRepository userRepository;

     public RequestService(RequestRepository repository, UserRepository userRepository){
         this.repository = repository;
         this.userRepository = userRepository;
     }

     @Transactional
     public void save(Request request, Principal principal) {
         if (principal != null) {
             Optional<User> userOpt = userRepository.findByEmail(principal.getName());
             if (userOpt.isPresent()) {
                 User user = userOpt.get();
                 request.setUser(user);
                 request.setName(user.getName());
                 request.setContact(user.getEmail());
                 request.setRolesMask(user.getRolesMask());
             }
         } else {
             Optional<User> userByEmail = userRepository.findByEmail(request.getContact());
             if (userByEmail.isPresent()) {
                 User user = userByEmail.get();
                 request.setUser(user);
                 request.setRolesMask(user.getRolesMask());
             } else {
                 request.setRolesMask((byte) 1);
             }
         }
         repository.save(request);
     }

     @SuppressWarnings("null")

     @Transactional(readOnly = true)
     public List<Request> findAllNewestFirst() {
         return repository.findAllByOrderByCreatedAtDesc();
     }

     @Transactional
     public void deleteById(UUID id) {
         repository.deleteById(id);
     }
}

