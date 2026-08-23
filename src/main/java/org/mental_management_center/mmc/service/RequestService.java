package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.model.enums.RequestStatus;
import org.mental_management_center.mmc.model.enums.RoleBit;
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
     public List<Request> getAdminRequestsSortedByDate() {
         return repository.findByRecipientIsNullOrderByCreatedAtDesc();
     }

    @Transactional(readOnly = true)
    public List<Request> getAdminRequestsSortedByUrgency() {
        return repository.findByRecipientIsNullSortedByUrgency();
    }

    @Transactional(readOnly = true)
    public List<Request> getAdminRequestsSortedByName() {
        return repository.findByRecipientIsNullOrderByNameAscCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Request> getAdminRequestsSortedByContact() {
        return repository.findByRecipientIsNullOrderByContactAscCreatedAtDesc();
    }

     @Transactional
     public void deleteById(UUID id) {
         repository.deleteById(id);
     }

    // Додати в RequestService.java
    @Transactional(readOnly = true)
    public Request findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Звернення не знайдено"));
    }

    @Transactional
    public void saveAdminReply(UUID id, String replyMessage) {
        Request request = findById(id);
        request.setAdminReply(replyMessage);
        request.setStatus(RequestStatus.ANSWERED);
        repository.save(request); // Звичайний save з JpaRepository (а не твій кастомний)
    }

    public List<Request> findByRecipientOrderByCreatedAtDesc(User recipient) {
        return repository.findByRecipientOrderByCreatedAtDesc(recipient);
    }

    public List<Request> getAdminRequests(User currentUser) {
        List<Request> allRequests = repository.findAll(); // Або твій існуючий метод отримання

        // Якщо це ТЕСТОВИЙ адмін (є біт 128) - залишаємо тільки тестові запити
        if (currentUser.hasRole(RoleBit.TEST)) {
            return allRequests.stream()
                    .filter(req -> req.getUser() != null && req.getUser().isTest())
                    .toList();
        }

        // Якщо це РЕАЛЬНИЙ адмін - відсікаємо тестове сміття
        return allRequests.stream()
                .filter(req -> req.getUser() != null && !req.getUser().isTest())
                .toList();
    }
}

