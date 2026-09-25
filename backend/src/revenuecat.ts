/**
 * The one RevenueCat server call this backend makes: granting a promotional entitlement.
 *
 * Uses the SECRET API key, which lives only in the Render environment — the key inside the Android
 * app is the public SDK one and cannot grant anything. An unconfigured or failing call degrades to
 * a logged warning rather than failing the approval: verification is the primary fact, and the
 * entitlement can be granted by hand in the dashboard afterwards.
 *
 * The app user id is the Firebase UID — the client logs into RevenueCat with it — so granting by
 * uid reaches the same customer the phone is already reading.
 *
 * The entitlement id must match the dashboard verbatim: the project's entitlement was created as
 * `omni_pro` and identifiers are locked once created.
 */
const RC_BASE = 'https://api.revenuecat.com/v1';
const OMNI_PLUS_ENTITLEMENT = 'omni_pro';

export interface RevenueCatDeps {
  secretApiKey: string | undefined;
  doctorCompDuration: string;
}

export interface RevenueCatService {
  grantDoctorEntitlement(appUserId: string): Promise<boolean>;
}

export function createRevenueCatService(deps: RevenueCatDeps): RevenueCatService {

  async function grantPromotionalEntitlement(
    appUserId: string,
    entitlementId: string,
    duration: string,
  ): Promise<boolean> {
    const response = await fetch(
      `${RC_BASE}/subscribers/${encodeURIComponent(appUserId)}/entitlements/${encodeURIComponent(entitlementId)}`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${deps.secretApiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ duration }),
      },
    );
    if (!response.ok) {
      console.error('[RC_COMP_ERROR] status=' + response.status + ' body=' + (await response.text()));
      return false;
    }
    return true;
  }

  return {
    async grantDoctorEntitlement(appUserId: string): Promise<boolean> {
      if (!deps.secretApiKey) {
        console.warn(
          '[RC_COMP_SKIPPED] REVENUECAT_SECRET_API_KEY is not set — grant ' +
            appUserId + ' the ' + OMNI_PLUS_ENTITLEMENT + ' entitlement manually in the dashboard',
        );
        return false;
      }
      const granted = await grantPromotionalEntitlement(appUserId, OMNI_PLUS_ENTITLEMENT, deps.doctorCompDuration);
      if (granted) {
        console.log('[RC_COMP] granted ' + OMNI_PLUS_ENTITLEMENT + ' to ' + appUserId + ' (' + deps.doctorCompDuration + ')');
      }
      return granted;
    },
  };
}
