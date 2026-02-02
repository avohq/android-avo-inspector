package app.avo.inspector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AvoAnonymousIdTests {

    @Before
    public void setUp() {
        AvoAnonymousId.clearCache();
        AvoInspector.avoStorage = null;
    }

    @Test
    public void testAnonymousIdGeneratedAndStored() {
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(any())).thenReturn(null);
        AvoInspector.avoStorage = mockStorage;

        String id = AvoAnonymousId.anonymousId();

        assertNotNull(id);
        verify(mockStorage).setItem(eq(AvoAnonymousId.storageKey), eq(id));
    }

    @Test
    public void testAnonymousIdLoadedFromStorage() {
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(eq(AvoAnonymousId.storageKey))).thenReturn("stored_id");
        AvoInspector.avoStorage = mockStorage;

        String id = AvoAnonymousId.anonymousId();

        assertEquals("stored_id", id);
    }

    @Test
    public void testAnonymousIdCached() {
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(any())).thenReturn(null);
        AvoInspector.avoStorage = mockStorage;

        String id1 = AvoAnonymousId.anonymousId();
        
        // Change storage return to ensure we don't read it again
        when(mockStorage.getItem(any())).thenReturn("new_id");
        
        String id2 = AvoAnonymousId.anonymousId();

        assertEquals(id1, id2);
        assertNotEquals("new_id", id2);
    }
    
    @Test
    public void testUnknownWhenNotInitialized() {
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(false);
        AvoInspector.avoStorage = mockStorage;
        
        String id = AvoAnonymousId.anonymousId();
        
        assertEquals("unknown", id);
    }
}
