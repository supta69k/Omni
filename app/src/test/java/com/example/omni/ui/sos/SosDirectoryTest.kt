package com.example.omni.ui.sos

import com.example.omni.data.model.Hospital
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SOS map's own arithmetic — [selectFacilities] — as a table of the decisions it is trusted to
 * make on an emergency screen.
 *
 * The function is the merged hospital + pharmacy directory's single sort/trim/search/choose point,
 * which is exactly why it is pure: every rule below is asserted against a list of data classes on
 * the JVM, with no Firestore, no location provider and no map view in the room. The numbers are
 * chosen so the radius and the fallback are exercised by name, not by accident.
 */
class SosDirectoryTest {

    /** Two facilities 0.0° of longitude apart at the equator are ~111 km apart; the tests use that. */
    private fun hospital(
        id: String,
        name: String = id,
        lat: Double,
        lng: Double,
        type: String = "Hospital",
        isPharmacy: Boolean = false,
    ) = Hospital(
        id = id,
        name = name,
        type = type,
        lat = lat,
        lng = lng,
        isPharmacy = isPharmacy,
    )

    private val directory = listOf(
        hospital("far", name = "Far General", lat = 0.0, lng = 2.0),
        hospital("mid", name = "Mid Clinic", lat = 0.0, lng = 0.05),
        hospital("near", name = "Near General", lat = 0.0, lng = 0.01),
        hospital(
            "pharma",
            name = "Near Pharmacy",
            lat = 0.0,
            lng = 0.02,
            type = "Pharmacy",
            isPharmacy = true,
        ),
    )

    @Test
    fun `without a fix the directory is alphabetical and unmeasured`() {
        val slice = selectFacilities(
            all = directory,
            fixLat = null,
            fixLng = null,
            typed = "",
            chosenId = null,
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )

        // Alphabetical: "Far General", "Mid Clinic", "Near General", "Near Pharmacy".
        assertEquals(listOf("far", "mid", "near", "pharma"), slice.visible.map { it.id })
        assertNull(slice.nearestId)
        assertNull(slice.visible.first().distanceKm)
    }

    @Test
    fun `with a fix the directory is distance-sorted and the nearest is named`() {
        val slice = selectFacilities(
            all = directory,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "",
            chosenId = null,
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )

        // Distance order — and "far" (222 km) is already trimmed by the 8 km radius, which the
        // radius test below asserts by name.
        assertEquals(listOf("near", "pharma", "mid"), slice.visible.map { it.id })
        assertEquals("near", slice.nearestId)
        // The nearest is also the default selection — the thing the camera and the card open on.
        assertEquals("near", slice.selected?.id)
        assertTrue(slice.visible.first().distanceKm!! < slice.visible.last().distanceKm!!)
    }

    @Test
    fun `the radius trims and a radius that finds nothing falls back to the closest few`() {
        // 1° of longitude is ~111 km, so only "far" is beyond the radius here.
        val trimmed = selectFacilities(
            all = directory,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "",
            chosenId = null,
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )
        assertTrue("far" !in trimmed.visible.map { it.id })
        assertEquals(3, trimmed.nearbySize)

        // Everything beyond the radius: the radius must still find something — the closest three.
        val remote = List(5) { index ->
            hospital("r$index", lat = 0.0, lng = 10.0 + index)
        }
        val fallback = selectFacilities(
            all = remote,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "",
            chosenId = null,
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )
        assertEquals(3, fallback.visible.size)
        assertEquals(listOf("r0", "r1", "r2"), fallback.visible.map { it.id })
    }

    @Test
    fun `a search filters the visible list but never moves the nearest`() {
        val slice = selectFacilities(
            all = directory,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "pharma",
            chosenId = null,
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )

        // The type matches, so the pharmacy is the only card — but "nearest" is read from the
        // unfiltered list, so the badge cannot land on a mere closest match.
        assertEquals(listOf("pharma"), slice.visible.map { it.id })
        assertEquals("near", slice.nearestId)
        assertEquals("pharma", slice.selected?.id)
    }

    @Test
    fun `the tapped pin wins the selection over the nearest`() {
        val slice = selectFacilities(
            all = directory,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "",
            chosenId = "mid",
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )

        assertEquals("mid", slice.selected?.id)
    }

    @Test
    fun `a tapped pin the search hides falls back to a visible card`() {
        val slice = selectFacilities(
            all = directory,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "pharma",
            chosenId = "near",
            filter = FacilityFilter.All,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )

        // "near" was tapped but the search hid it; the sheet must still carry a card.
        assertEquals("pharma", slice.selected?.id)
    }

    @Test
    fun `the pharmacy chip scopes the radius, the nearest and the selection to pharmacies`() {
        val slice = selectFacilities(
            all = directory,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "",
            chosenId = null,
            filter = FacilityFilter.Pharmacies,
            nearbyRadiusKm = 8.0,
            fallbackCount = 3,
        )

        // "near" (a hospital) is closer, but the Pharmacies chip must not let a hospital be the
        // nearest or the selection — the chip answers "where is the nearest pharmacy".
        assertEquals(listOf("pharma"), slice.visible.map { it.id })
        assertEquals("pharma", slice.nearestId)
        assertEquals("pharma", slice.selected?.id)
        // The empty-state banner reads the *unfiltered* nearby count, so a chip that found nothing
        // must never be mistaken for "the directory hasn't reached this phone yet".
        assertEquals(3, slice.nearbySize)
    }

    @Test
    fun `a filter that finds nothing in the radius falls back to the closest of that kind`() {
        // Push the only pharmacy out to ~2,200 km: the 8 km radius slice holds hospitals only.
        val remotePharmacy = directory.map {
            if (it.isPharmacy) it.copy(lat = 0.0, lng = 20.0) else it
        }

        val slice = selectFacilities(
            all = remotePharmacy,
            fixLat = 0.0,
            fixLng = 0.0,
            typed = "",
            chosenId = null,
            filter = FacilityFilter.Pharmacies,
            nearbyRadiusKm = 8.0,
            fallbackCount = 2,
        )

        // The kind gets the same mercy the unfiltered list had — the closest few of that kind,
        // never nothing. The two visible cards are the directory's one pharmacy and nothing else
        // of the other kind leaking in.
        assertEquals(listOf("pharma"), slice.visible.map { it.id })
        assertEquals("pharma", slice.nearestId)
    }
}
