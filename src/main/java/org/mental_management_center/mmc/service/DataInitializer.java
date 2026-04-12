package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Article;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Використовуємо реальний email адміністратора
        String adminEmail = "mental.m.center@gmail.com";
        User admin = userRepository.findByEmail(adminEmail).orElse(null);

        if (admin == null) {
            System.out.println("⚠️ АДМІНІСТРАТОРА НЕ ЗНАЙДЕНО. Перевір email у базі!");
            return;
        }

        // 2. Створення статтей (всі 8 тем)
        
        saveNewArticle(admin, "inner-calm", "Тривога та панічні стани", 
            "Тривога — це не помилка вашого організму. Це голос вашого найдавнішого захисника.",
            "<p>Тривога — це не помилка вашого організму. Це голос захисника. Робота з тривогою починається з розуміння її механізмів.</p>",
            Set.of("Терапія", "Тривога"));

        saveNewArticle(admin, "restore-resource", "Відновлення ресурсу та подолання вигорання", 
            "Професійне та особистісне вигорання — коли вогонь перетворився на попіл.",
            "<p>Людина може витримати майже будь-яке 'Як', якщо вона знає 'Навіщо'. Відновлення — це не тільки сон, а й перегляд кордонів.</p>",
            Set.of("Вигорання", "Сенси"));

        saveNewArticle(admin, "closeness-crisis", "Стосунки та кризи близькості", 
            "Криза у стосунках — це не кінець кохання. Метафора ракети.",
            "<p>Уявіть стосунки як ракету... Кожна криза — це момент скидання корпусу. Це боляче, але необхідно для виходу на нову орбіту.</p>",
            Set.of("Сім'я", "Кризи"));

        saveNewArticle(admin, "new-meanings", "Депресивні стани та пошук нових сенсів", 
            "Депресія — це витрата ресурсу на енергію зупинки.",
            "<p>Сенси не знаходяться, вони створюються дією. Навіть найменшим кроком. Депресія — це не відсутність сил, а їх витрата на утримання болю.</p>",
            Set.of("Депресія", "Сенс"));

        saveNewArticle(admin, "freedom-choice", "Залежність: Даність, а не вирок", 
            "Залежність — втеча від нестерпної реальності.",
            "<p>За будь-якою залежністю стоїть бажання 'вимкнути' реальність. Ми працюємо не над заборонами, а над автономією.</p>",
            Set.of("Автономія", "Вибір"));

        saveNewArticle(admin, "dialogue", "Конфлікти та медіація", 
            "Конфлікт — це не завжди війна. Це зіткнення двох різних реальностей.",
            "<p>Конфлікт — це зіткнення двох різних реальностей. У медіації я створюю безпечний простір, де кожен може висловити свій біль і потреби.</p>",
            Set.of("Медіація", "Діалог"));

        saveNewArticle(admin, "exit-nearby", "Ілюзія, що виходу нема — Вихід поруч", 
            "Фундаментальна психологічна зміна фокуса. Ваша стійкість сьогодні — спадок дітям.",
            "<p>Колись у метро був напис «Виходу немає». Тепер там написано: «Вихід поруч». Вихід не зник — він просто там, де ви ще не звикли його шукати.</p>",
            Set.of("Стійкість", "Криза"));

        saveNewArticle(admin, "default", "Психологічна допомога", 
            "Оберіть тему, яка вас турбує, щоб отримати більше інформації та підтримку.",
            "<p>Конфлікт — це часто точка зросту. У медіації ми розглядаємо конфлікт як зіткнення потреб, де кожна сторона має право бути почутою.</p>",
            Set.of("Терапія", "Підтримка"));
    }

    private void saveNewArticle(User author, String category, String title, 
                                String description, String content, Set<String> tags) {
        
        // Перевіряємо за заголовком, щоб не створювати дублікати
        if (articleRepository.existsByTitle(title)) {
            return;
        }

        Article article = Article.builder()
                .author(author)
                .category(category)
                .title(title)
                .description(description)
                .tags(tags)
                .publishedAt(LocalDateTime.now())
                .build();

        // Сеттер автоматично зробить компресію контенту
        article.setContent(content);

        articleRepository.save(article);
    }
}