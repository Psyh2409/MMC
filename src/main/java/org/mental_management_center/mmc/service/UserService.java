package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.Role;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
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
            throw new RuntimeException("This email address is already taken");
        if (user.getRoles() == null || user.getRoles().isEmpty())
            user.addRole(Role.READER);
        if (user.getAuthProvider() == null || user.getAuthProvider().isEmpty())
            user.setAuthProvider("LOCAL");
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findUserByEmail(email);
    }

    public User upsertOAuth2User(String email, String name, String provider, String providerId) {
        return userRepository.findUserByEmail(email)
                .map(existingUser -> {
                    if ((existingUser.getName() == null || existingUser.getName().isBlank())
                            && name != null && !name.isBlank()) {
                        existingUser.setName(name);
                    }
                    if (existingUser.getAuthProvider() == null || existingUser.getAuthProvider().isBlank()) {
                        existingUser.setAuthProvider(provider);
                    }
                    if (providerId != null && !providerId.isBlank()) {
                        existingUser.setProviderId(providerId);
                    }
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setName((name == null || name.isBlank()) ? email : name);
                    user.addRole(Role.READER);
                    user.setAuthProvider(provider);
                    user.setProviderId(providerId);
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    return userRepository.save(user);
                });
    }

    public void promoteToClient(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Der Benutzer wurde nicht gefunden"));
        user.addRole(Role.CLIENT);
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
