package org.mental_management_center.mmc.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AppErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusCode instanceof Integer
                ? (Integer) statusCode
                : HttpStatus.INTERNAL_SERVER_ERROR.value();

        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        response.setStatus(httpStatus.value());
        model.addAttribute("statusCode", httpStatus.value());
        model.addAttribute("errorTitle", titleFor(httpStatus));
        model.addAttribute("errorMessage", messageFor(httpStatus));
        return "error";
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
