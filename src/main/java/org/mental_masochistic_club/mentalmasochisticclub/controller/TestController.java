package org.mental_masochistic_club.mentalmasochisticclub.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/hello")
    public String sayHello(){
        return "Привіт! Контролер працює, проєкт живий! \uD83D\uDE80";
    }
}
