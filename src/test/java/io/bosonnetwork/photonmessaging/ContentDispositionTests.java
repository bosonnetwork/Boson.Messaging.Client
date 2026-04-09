package io.bosonnetwork.photonmessaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ContentDispositionTests {
	@Test
	void testInline() {
		ContentDisposition cd = ContentDisposition.parse("inline");
		assertTrue(cd.isInline());
		assertNull(cd.getFilename());
		assertEquals("inline", cd.getValue());
	}

	@Test
	void testAttachment() {
		ContentDisposition cd = ContentDisposition.parse("attachment");
		assertTrue(cd.isAttachment());
		assertNull(cd.getFilename());
		assertEquals("attachment", cd.getValue());
	}

	@Test
	void testAttachmentWithFilename() {
		String header = "attachment; filename=\"file name.jpg\"";
		ContentDisposition cd = ContentDisposition.parse(header);
		assertTrue(cd.isAttachment());
		assertEquals("file name.jpg", cd.getFilename());
		assertEquals("file name.jpg", cd.getAsciiFilename());
		// Note: parse doesn't automatically synthesize rfc5987 if not present in input
		assertEquals("attachment; filename=\"file name.jpg\"", cd.getValue());
	}

	@Test
	void testAttachmentWithRfc5987Filename() {
		String header = "attachment; filename*=UTF-8''file%20name.jpg";
		ContentDisposition cd = ContentDisposition.parse(header);
		assertTrue(cd.isAttachment());
		assertEquals("file name.jpg", cd.getFilename());
		assertEquals("UTF-8''file%20name.jpg", cd.getRfc5987Filename());
		assertEquals("attachment; filename*=UTF-8''file%20name.jpg", cd.getValue());
	}

	@Test
	void testAttachmentWithBothFilenames() {
		String header = "attachment; filename=\"file_name.jpg\"; filename*=UTF-8''file%20name.jpg";
		ContentDisposition cd = ContentDisposition.parse(header);
		assertTrue(cd.isAttachment());
		// RFC 5987 should be preferred
		assertEquals("file name.jpg", cd.getFilename());
		assertEquals("file_name.jpg", cd.getAsciiFilename());
		assertEquals("UTF-8''file%20name.jpg", cd.getRfc5987Filename());
	}

	@Test
	void testCreateInline() {
		ContentDisposition cd = ContentDisposition.inline();
		assertTrue(cd.isInline());
		assertNull(cd.getFilename());
	}

	@Test void testCreateAttachmentWithAscii() {
		ContentDisposition cd = ContentDisposition.attachment("file name.jpg");
		assertTrue(cd.isAttachment());
		assertEquals("file name.jpg", cd.getFilename());
		assertEquals("file name.jpg", cd.getAsciiFilename());
		assertEquals("UTF-8''file%20name.jpg", cd.getRfc5987Filename());
		assertEquals("attachment; filename=\"file name.jpg\"; filename*=UTF-8''file%20name.jpg", cd.getValue());
	}

	@Test
	void testCreateAttachmentWithUnicode() {
		ContentDisposition cd = ContentDisposition.attachment("fïle nãme.jpg");
		assertTrue(cd.isAttachment());
		assertEquals("fïle nãme.jpg", cd.getFilename());
		assertEquals("file name.jpg", cd.getAsciiFilename()); // normalized/fallback
		assertEquals("UTF-8''f%C3%AFle%20n%C3%A3me.jpg", cd.getRfc5987Filename());
		assertEquals("attachment; filename=\"file name.jpg\"; filename*=UTF-8''f%C3%AFle%20n%C3%A3me.jpg", cd.getValue());
	}

	@Test
	void testCommonErrorFormats() {
		// Invalid type
		assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse("unknown"));

		// Malformed RFC 5987 (missing quotes)
		assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse("attachment; filename*=UTF-8-file.jpg"));

		// Null header
		assertThrows(NullPointerException.class, () -> ContentDisposition.parse(null));

		// Empty header
		assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse(""));

		// Empty type before semicolon
		assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse("; filename=\"test.txt\""));
	}
}