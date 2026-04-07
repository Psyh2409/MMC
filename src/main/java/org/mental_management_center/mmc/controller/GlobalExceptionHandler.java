package org.mental_management_center.mmc.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNotFound(NoResourceFoundException exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        fillModel(model, HttpStatus.NOT_FOUND);
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        fillModel(model, HttpStatus.FORBIDDEN);
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        fillModel(model, HttpStatus.INTERNAL_SERVER_ERROR);
        return "error";
    }

    private void fillModel(Model model, HttpStatus status) {
        model.addAttribute("statusCode", status.value());
        model.addAttribute("errorTitle", titleFor(status));
        model.addAttribute("errorMessage", messageFor(status));
    }

    private String titleFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Сторінку не знайдено";
            case FORBIDDEN -> "Доступ обмежено";
            case BAD_REQUEST -> "Некоректний запит";
            default -> "Щось пішло не так";
        };
    }

    private String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Схоже, що потрібна сторінка відсутня або її адресу змінено.";
            case FORBIDDEN -> "У вас зараз немає прав доступу до цієї сторінки.";
            case BAD_REQUEST -> "Запит виглядає некоректним. Спробуйте повторити дію ще раз.";
            default -> "Виникла внутрішня помилка. Спробуйте оновити сторінку або повернутися пізніше.";
        };
    }
}
