// Flag SVGs from the flag-icons package (MIT). Only the flags we need are bundled: the glob below lists
// the countries of the 32 NBP table A currencies plus PL, and Vite resolves it at build time to their URLs.
// (Importing the package's CSS instead would pull all 271 flags into the build.)
const flagUrls = import.meta.glob<string>(
  '/node_modules/flag-icons/flags/4x3/{au,br,ca,ch,cl,cn,cz,dk,eu,gb,hk,hu,id,il,in,is,jp,kr,mx,my,no,nz,ph,pl,ro,se,sg,th,tr,ua,us,za}.svg',
  { eager: true, query: '?url', import: 'default' },
)

/**
 * ISO 4217 rule: the first two letters of a currency code are the ISO 3166 country code
 * (USD -> us, EUR -> eu, PLN -> pl). X-codes are supranational (XDR = IMF Special Drawing Rights)
 * and have no flag.
 */
function flagUrl(code: string): string | undefined {
  const country = code.slice(0, 2).toLowerCase()
  return flagUrls[`/node_modules/flag-icons/flags/4x3/${country}.svg`]
}

interface Props {
  code: string
}

/**
 * Small flag for a currency. Decorative: the code is always written next to it, so screen readers skip it
 * (empty alt). A currency without a flag gets a neutral "¤" (the generic currency sign) instead.
 */
export function CurrencyFlag({ code }: Props) {
  const url = flagUrl(code)
  if (!url) {
    return (
      <span className="flag flag-fallback" aria-hidden="true">
        ¤
      </span>
    )
  }
  return <img className="flag" src={url} alt="" width={20} height={15} />
}
