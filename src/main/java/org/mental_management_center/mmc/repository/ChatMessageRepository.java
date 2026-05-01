package org.mental_management_center.mmc.repository;

import org.mental_management_center.mmc.model.ChatMessage;
import org.mental_management_center.mmc.model.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // Отримуємо історію чату між двома користувачами, відсортовану за часом
    @Query("SELECT m FROM ChatMessage m WHERE " +
            "(m.senderId = :userA AND m.recipientId = :userB) OR " +
            "(m.senderId = :userB AND m.recipientId = :userA) " +
            "ORDER BY m.timestamp ASC")
    List<ChatMessage> findChatHistory(@Param("userA") UUID userA, @Param("userB") UUID userB);

    // Знайти історію, де юзер бере участь, але ЦЕ НЕ ПУБЛІЧНИЙ ЧАТ
    // Рядок 'PUBLIC' замінено на параметр publicRoomId для сумісності з UUID
    @Query("SELECT m FROM ChatMessage m WHERE (m.senderId = :userId OR m.recipientId = :userId) " +
            "AND m.recipientId != :publicRoomId ORDER BY m.timestamp ASC")
    List<ChatMessage> findPrivateConversationHistory(@Param("userId") UUID userId, @Param("publicRoomId") UUID publicRoomId);

    // ВИПРАВЛЕНО: countBy... не може повертати List. Змінено на findBy...
    // Якщо потрібна була саме кількість, метод мав би повертати long і називатися countBy...
    List<ChatMessage> findByRecipientIdAndStatus(UUID recipientId, MessageStatus status);

    // Отримуємо історію спільного чату, відсортовану за часом
    List<ChatMessage> findByRecipientIdOrderByTimestampAsc(UUID recipientId);

}

//package org.mental_management_center.mmc.repository;
//
//import org.mental_management_center.mmc.model.ChatMessage;
//import org.mental_management_center.mmc.model.MessageStatus;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.UUID;
//
//@Repository
//public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
//
//    // Отримуємо історію чату між двома користувачами, відсортовану за часом
//    @Query("SELECT m FROM ChatMessage m WHERE " +
//            "(m.senderId = :userA AND m.recipientId = :userB) OR " +
//            "(m.senderId = :userB AND m.recipientId = :userA) " +
//            "ORDER BY m.timestamp ASC")
//    List<ChatMessage> findChatHistory(@Param("userA") String userA, @Param("userB") String userB);
//
//    // Знайти історію, де юзер бере участь, але ЦЕ НЕ ПУБЛІЧНИЙ ЧАТ
//    @Query("SELECT m FROM ChatMessage m WHERE (m.senderId = :userId OR m.recipientId = :userId) " +
//            "AND m.recipientId != 'PUBLIC' ORDER BY m.timestamp ASC")
//    List<ChatMessage> findPrivateConversationHistory(@Param("userId") String userId);
//
//    // Знайти всі непрочитані повідомлення для конкретного користувача
//    List<ChatMessage> countByRecipientIdAndStatus(String recipientId, MessageStatus status);
//
//    // Отримуємо історію спільного чату, відсортовану за часом
//    List<ChatMessage> findByRecipientIdOrderByTimestampAsc(String recipientId);
//
//}