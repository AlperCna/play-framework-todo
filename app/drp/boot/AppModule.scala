package drp.boot

import com.google.inject.AbstractModule

class AppModule extends AbstractModule {
  override def configure(): Unit = {
    // DRP modulleri buraya install edilecek (asset, discovery, candidate, ...)
  }
}
