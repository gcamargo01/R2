/* NotifiedCoreModule.java */
package uy.com.r2.core;

/** R2 notified core module interface.
 * A CoreModule interface that handles local configuration changes.
 * @author G.Camargo
 */
public interface NotifiedCoreModule extends CoreModule {
    
    /** Configuration change.
     * @param moduleName Module name
     */
    public void onModuleUpdate( String moduleName);
    
    /** Configuration change.
     * @param moduleName Module name
     */
    public void onModuleRemove( String moduleName);

}
