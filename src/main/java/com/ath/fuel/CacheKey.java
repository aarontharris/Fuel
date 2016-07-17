package com.ath.fuel;


class CacheKey {

    public static final Integer DEFAULT_FLAVOR = null;

    public static CacheKey attain( Class<?> leafType ) {
        return new CacheKey( leafType, DEFAULT_FLAVOR );
    }

    public static CacheKey attain( Class<?> leafType, Integer flavor ) {
        return new CacheKey( leafType, flavor );
    }

    public static CacheKey attain( Lazy lazy ) {
        return new CacheKey( lazy.leafType, lazy.getFlavor() );
    }


    private Class<?> leafType; // compared by address not by value so be careful
    private Integer flavor; // compared by address not by value so be careful

    private CacheKey( Class<?> leafType ) {
        this( leafType, DEFAULT_FLAVOR );
    }

    private CacheKey( Class<?> leafType, Integer flavor ) {
        this.leafType = leafType;
        this.flavor = flavor;
    }

    public Class<?> getLeafType() {
        return leafType;
    }

    public Integer getFlavor() {
        return flavor;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( flavor == null ) ? 0 : flavor.hashCode() );
        result = prime * result + ( ( leafType == null ) ? 0 : leafType.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        CacheKey other = (CacheKey) obj;
        if ( flavor == null ) {
            if ( other.flavor != null ) {
                return false;
            }
        } else if ( !flavor.equals( other.flavor ) ) {
            return false;
        }
        if ( leafType == null ) {
            if ( other.leafType != null ) {
                return false;
            }
        } else if ( !leafType.equals( other.leafType ) ) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CacheKey[" + hashCode() + "] " + leafType.getSimpleName() + ", flavor=" + flavor;
    }

}
