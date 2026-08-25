package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.PublicPost;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.repository.PublicPostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicPostService {

    private final PublicPostRepository publicPostRepository;
    private final FileStorageService fileStorageService;
    private final ArticleRepository articleRepository;

    public Page<PublicPost> getPostsByAuthor(UUID authorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return publicPostRepository.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable);
    }

    // Для майбутньої головної сторінки
    public Page<PublicPost> getAllPublicPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return publicPostRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public PublicPost createPost(User author, String content, MultipartFile mediaFile) {
        String fileName = null;

        if (mediaFile != null && !mediaFile.isEmpty()) {
            // Зберігаємо як звичайний публічний файл (не приватний!)
            fileName = fileStorageService.storeFile(mediaFile);
        }

        PublicPost post = PublicPost.builder()
                .author(author)
                .content(content)
                .mediaFileName(fileName)
                .build();

        return publicPostRepository.save(post);
    }

    @Transactional
    public void deletePost(UUID postId, User currentUser) {
        PublicPost post = publicPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        if (!post.getAuthor().getId().equals(currentUser.getId()) && !currentUser.isAdmin()) {
            throw new RuntimeException("Немає прав для видалення цього поста");
        }

        String fileName = post.getMediaFileName();

        // 1. Спочатку видаляємо запис із бази даних
        publicPostRepository.delete(post);

        // 2. Перевіряємо, чи цей публічний файл ще десь використовується (в інших постах або статтях)
        if (fileName != null) {
            long articleUsage = articleRepository.countArticleUsage(fileName);
            long publicPostUsage = publicPostRepository.countByMediaFileName(fileName);

            // Видаляємо фізично лише якщо посилань більше немає взагалі
            if (articleUsage + publicPostUsage == 0) {
                fileStorageService.deletePublicFileIfUnused(fileName);
            }
        }
    }

    @Transactional
    public void updatePost(UUID postId, String newContent, User currentUser) {
        PublicPost post = publicPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Пост не знайдено"));

        // Перевірка безпеки
        if (!post.getAuthor().getId().equals(currentUser.getId()) && !currentUser.isAdmin()) {
            throw new RuntimeException("Немає прав для редагування цього поста");
        }

        post.setContent(newContent);
        publicPostRepository.save(post);
    }
}