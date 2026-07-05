package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.PublicPost;
import org.mental_management_center.mmc.model.User;
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

        // Перевірка безпеки: видалити може тільки автор або адмін
        if (!post.getAuthor().getId().equals(currentUser.getId()) && !currentUser.isAdmin()) {
            throw new RuntimeException("Немає прав для видалення цього поста");
        }

        if (post.getMediaFileName() != null) {
            fileStorageService.deletePublicFile(post.getMediaFileName());
        }

        publicPostRepository.delete(post);
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