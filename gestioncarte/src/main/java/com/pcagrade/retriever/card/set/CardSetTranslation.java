package com.pcagrade.retriever.card.set;

import com.pcagrade.retriever.localization.translation.AbstractTranslationEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import org.hibernate.annotations.DiscriminatorOptions;

import java.time.LocalDateTime;

@Entity
@Table(name = "card_set_translation")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "discriminator")
@DiscriminatorValue("bas")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CardSetTranslation extends AbstractTranslationEntity<CardSet> {

	@Column(name = "discriminator")
	protected String discriminator;  // <-- Ajoutez ce champ

	// Ajoutez ces méthodes
	public String getDiscriminator() {
		return discriminator;
	}

	public void setDiscriminator(String discriminator) {
		this.discriminator = discriminator;
	}

	@Column
	private boolean available;

	@Column(name = "release_date")
	private LocalDateTime releaseDate;

	@Column(name = "label_name")
	private String labelName;

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public LocalDateTime getReleaseDate() {
		return releaseDate;
	}

	public void setReleaseDate(LocalDateTime releaseDate) {
		this.releaseDate = releaseDate;
	}

	public String getLabelName() {
		return labelName;
	}

	public void setLabelName(String labelName) {
		this.labelName = labelName;
	}
}
