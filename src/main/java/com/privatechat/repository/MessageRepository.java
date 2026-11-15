package com.privatechat.repository;

import com.privatechat.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId AND m.isDeleted = false ORDER BY m.sentAt ASC")
    List<Message> findByRoomIdOrderBySentAt(Long roomId);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId AND m.isDeleted = false ORDER BY m.sentAt DESC")
    List<Message> findRecentByRoomId(Long roomId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.room.id = :roomId AND m.isDeleted = false")
    long countByRoomId(Long roomId);
}

