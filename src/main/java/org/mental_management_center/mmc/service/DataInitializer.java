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
// import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // Створюємо автора
        User admin = userRepository.findById(UUID.fromString("a46beb09-a977-4c61-8e79-28ad47cb7bda"))
            .orElseThrow(() -> new IllegalArgumentException("Author not found"));

        // 1. Тривога
        createArticleIfAbsent(admin, "inner-calm", "Тривога та панічні стани", 
            "Тривога — це не помилка вашого організму. Це голос вашого найдавнішого захисника.",
            "<p>Тривога — це не помилка вашого організму... [тут повний текст з inner-calm.html]</p>");

        // 2. Відновлення ресурсу
        createArticleIfAbsent(admin, "restore-resource", "Відновлення ресурсу та подолання вигорання", 
            "Професійне та особистісне вигорання — це коли внутрішній вогонь перетворився на попіл.",
            "<p>Людина може витримати майже будь-яке 'Як', якщо вона знає 'Навіщо'...</p>");

        // 3. Кризи близькості
        createArticleIfAbsent(admin, "closeness-crisis", "Стосунки та кризи близькості", 
            "Криза у стосунках — це не кінець кохання. Метафора ракети: як ми ростемо разом.",
            "<p>Уявіть стосунки як ракету, що летить до зірок... Кожна криза — це момент скидання старого корпусу.</p>");

        // 4. Депресивні стани
        createArticleIfAbsent(admin, "new-meanings", "Депресивні стани та пошук нових сенсів", 
            "Депресія — це стан, коли людина витрачає енергію на те, щоб зупинити власне життя.",
            "<p>Депресія — це не відсутність сил. Це витрата ресурсу на 'енергію зупинки'...</p>");

        // 5. Залежна поведінка
        createArticleIfAbsent(admin, "freedom-choice", "Залежність: Даність, а не вирок", 
            "Залежність — це специфічний спосіб організації особистості та втеча від реальності.",
            "<p>За будь-якою залежністю стоїть бажання 'вимкнути' реальність. Ми працюємо над автономією.</p>");

        // 6. Конфлікти та медіація
        createArticleIfAbsent(admin, "dialogue", "Конфлікти та медіація", 
            "Конфлікт — це не завжди війна. Це зіткнення двох різних реальностей.",
            "<p>Моє завдання — створити безпечний простір, де кожен може висловити свій біль.</p>");

        // 7. Вихід поруч
        createArticleIfAbsent(admin, "exit-nearby", "Ілюзія, що виходу нема — Вихід поруч", 
            "Фундаментальна психологічна зміна фокуса. Ваша стійкість сьогодні — це спадок вашим дітям.",
            "<p>Колись у метро був напис «Виходу немає». Тепер там написано: «Вихід поруч».</p>");
            
        // 8. Дефолтна заглушка (якщо десь посилання зламається)
        createArticleIfAbsent(admin, "default", "Психологічна допомога", 
            "Оберіть тему, яка вас турбує, щоб отримати більше інформації.",
            "<p>Конфлікт — це не обов'язково кінець стосунків, це часто точка їхнього зросту...</p>");
    }

    private void createArticleIfAbsent(User admin, String id, String title, String description, String content) {
        UUID uuid = UUID.nameUUIDFromBytes(id.getBytes());
        if (uuid != null && !articleRepository.existsById(uuid)) {
            Article article = new Article(admin.getId());
            article.setTitle(title);
            article.setDescription(description);
            article.setContent(content); // ТУТ МАЄ БУТИ ВЕСЬ ТЕКСТ З HTML
            article.setCategory("Психологія");
            article.setPublishedAt(LocalDateTime.now());
            article.setTags(Set.of("Терапія", "Сенси"));
            articleRepository.save(article);
        }
    }
}