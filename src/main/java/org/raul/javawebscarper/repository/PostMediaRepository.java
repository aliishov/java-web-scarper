package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

	List<PostMedia> findByPostOrderByPositionAsc(Post post);

	Optional<PostMedia> findByPostAndMediaUrl(Post post, String mediaUrl);
}
