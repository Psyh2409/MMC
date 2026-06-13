package org.mental_management_center.mmc.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@Slf4j
@ControllerAdvice
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletResponse response, Model model) {
        log.warn("Спроба дублювання даних (можливо, подвійний клік при реєстрації): {}", exception.getMessage());

        response.setStatus(HttpStatus.CONFLICT.value()); // Встановлюємо статус 409 Conflict

        // Використовуємо твою існуючу логіку шаблону error.html
        model.addAttribute("statusCode", 409);
        model.addAttribute("errorTitle", "Дані вже існують");
        model.addAttribute("errorMessage", "Користувач з такою електронною поштою вже зареєстрований. Будь ласка, перейдіть на сторінку входу або скористайтеся іншою поштою.");

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

    // Видаляємо обидва методи (handleMediaDisconnects та handleClientAbort)
    // і вставляємо один цей:

    @ExceptionHandler({
            java.io.IOException.class,
            AsyncRequestNotUsableException.class
    })
    @ResponseBody
    public void handleMediaDisconnects(Exception ex) {
        // Логуємо лише на рівні DEBUG або WARN, щоб не "засмічувати" консоль
        log.warn("Штатний обрив медіа-потоку (користувач закрив вкладку або пауза): {}", ex.getMessage());
    }
}
