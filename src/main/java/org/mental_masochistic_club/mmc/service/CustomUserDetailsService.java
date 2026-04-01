package org.mental_masochistic_club.mmc.service;

import org.mental_masochistic_club.mmc.model.User;
import org.mental_masochistic_club.mmc.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List; // Ось цей List, який тобі потрібен

@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    private  final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No User with with email: "+ email +" not found."));

        return new MyUserDetails(user, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));

//                new org.springframework.security.core.userdetails.User(
//                user.getEmail(),
//                user.getPassword(),
//                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
//        );
    }
}
