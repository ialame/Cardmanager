package com.pcagrade.painter.image;

import com.github.f4b6a3.ulid.Ulid;
import com.pcagrade.mason.jpa.repository.MasonRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface ImageRepository extends MasonRepository<Image, Ulid> {

    @Query(value = "SELECT nextval('public.image_id')", nativeQuery = true)
    long getNextImageId();

    Optional<Image> findByPath(String path);

}
