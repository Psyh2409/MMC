package org.mental_masochistic_club.mmc.service;

import org.mental_masochistic_club.mmc.model.User;
import org.mental_masochistic_club.mmc.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerNewUser(User user) {
        if(userRepository.existsByEmail(user.getEmail()))
            throw new RuntimeException("Diese E-Mail-Adresse ist bereits vergeben");
        if (user.getRole() == null || user.getRole().isEmpty())
            user.setRole("USER");
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findUserByEmail(email);
    }

    public void promoteToClient(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));
        user.setRole("CLIENT");
        userRepository.save(user);
    }

    public void deleteUserById(Long id, String currentAdminEmail) {
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));
        if (userToDelete.getEmail().equals(currentAdminEmail)) {
            throw new RuntimeException("Sie konnen Ihr eigenes Administratorkonto nicht loschen!");
        }
        userRepository.deleteById(id);
    }
}
