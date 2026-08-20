package hackathon.app.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hackathon.app.auth.application.AuthService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.image.entity.StoredImage;
import hackathon.app.image.repository.StoredImageRepository;
import hackathon.app.user.domain.User;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {
    private static final String SESSION_ID = "session-id";
    private static final Long IMAGE_ID = 11L;
    private static final Long UPLOADER_ID = 1L;
    private static final Long VIEWER_ID = 2L;
    private static final String STORAGE_KEY = "images/puzzle.jpg";

    @Mock StoredImageRepository images;
    @Mock ObjectStorage storage;
    @Mock ImageOwnerValidator ownerValidator;
    @Mock ImageReadAccessPolicy readAccessPolicy;
    @Mock AuthService auth;
    @Mock StoredImage image;
    @Mock User user;
    @InjectMocks ImageService service;

    @Test
    void getReturnsFreshPresignedUrlAndExpirationOnEveryRequest() {
        Instant firstExpiration = Instant.now().plusSeconds(600);
        Instant secondExpiration = Instant.now().plusSeconds(601);
        when(images.findByIdAndDeletedAtIsNull(IMAGE_ID)).thenReturn(Optional.of(image));
        when(auth.findUser(SESSION_ID)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(VIEWER_ID);
        when(image.getStorageKey()).thenReturn(STORAGE_KEY);
        when(storage.signedGetUrl(STORAGE_KEY)).thenReturn(
            new ObjectStorage.SignedUrl("https://s3.example/first", firstExpiration),
            new ObjectStorage.SignedUrl("https://s3.example/second", secondExpiration)
        );

        ImageService.ImageResult first = service.get(SESSION_ID, IMAGE_ID);
        ImageService.ImageResult second = service.get(SESSION_ID, IMAGE_ID);

        assertThat(first.url()).isEqualTo("https://s3.example/first");
        assertThat(first.expiresAt()).isEqualTo(firstExpiration);
        assertThat(second.url()).isEqualTo("https://s3.example/second");
        assertThat(second.expiresAt()).isEqualTo(secondExpiration);
        assertThat(first.expiresAt()).isAfter(Instant.now());
        assertThat(second.expiresAt()).isAfter(Instant.now());
        verify(readAccessPolicy, times(2)).check(image, VIEWER_ID);
        verify(storage, times(2)).signedGetUrl(STORAGE_KEY);
    }

    @Test
    void anonymousRequestIsPassedToReadPolicy() {
        Instant expiration = Instant.now().plusSeconds(600);
        when(images.findByIdAndDeletedAtIsNull(IMAGE_ID)).thenReturn(Optional.of(image));
        when(auth.findUser(null)).thenReturn(Optional.empty());
        when(image.getStorageKey()).thenReturn(STORAGE_KEY);
        when(storage.signedGetUrl(STORAGE_KEY))
            .thenReturn(new ObjectStorage.SignedUrl("https://s3.example/public", expiration));

        ImageService.ImageResult result = service.get(null, IMAGE_ID);

        assertThat(result.url()).isEqualTo("https://s3.example/public");
        assertThat(result.expiresAt()).isEqualTo(expiration);
        verify(readAccessPolicy).check(image, null);
    }

    @Test
    void deletedImageKeepsImageNotFoundContract() {
        when(images.findByIdAndDeletedAtIsNull(IMAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(null, IMAGE_ID))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).errorCode())
            .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);
        verifyNoInteractions(auth, readAccessPolicy, storage);
    }

    @Test
    void deleteRemainsUploaderOnly() {
        when(auth.requireUser(SESSION_ID)).thenReturn(user);
        when(user.getId()).thenReturn(VIEWER_ID);
        when(images.findByIdAndDeletedAtIsNull(IMAGE_ID)).thenReturn(Optional.of(image));
        when(image.getUploaderUserId()).thenReturn(UPLOADER_ID);

        assertThatThrownBy(() -> service.delete(SESSION_ID, IMAGE_ID))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).errorCode())
            .isEqualTo(ErrorCode.IMAGE_ACCESS_DENIED);
        verifyNoInteractions(storage);
    }

    @Test
    void getRandomByCategoryNameReturnsFreshSignedUrl() {
        Instant expiration = Instant.now().plusSeconds(600);
        when(images.findRandomActiveByCategoryCode("EXERCISE")).thenReturn(Optional.of(image));
        when(image.getStorageKey()).thenReturn(STORAGE_KEY);
        when(storage.signedGetUrl(STORAGE_KEY))
            .thenReturn(new ObjectStorage.SignedUrl("https://s3.example/random", expiration));

        ImageService.ImageResult result = service.getRandomByCategoryName("운동");

        assertThat(result.image()).isSameAs(image);
        assertThat(result.url()).isEqualTo("https://s3.example/random");
        assertThat(result.expiresAt()).isEqualTo(expiration);
    }

    @Test
    void getRandomByCategoryNameThrowsWhenImagePoolIsEmpty() {
        when(images.findRandomActiveByCategoryCode("EXERCISE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRandomByCategoryName("운동"))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).errorCode())
            .isEqualTo(ErrorCode.IMAGE_NOT_FOUND_IN_CATEGORY);
        verifyNoInteractions(storage);
    }

}
