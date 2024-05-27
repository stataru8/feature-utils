            <plugin>
                <groupId>os.local.esb</groupId>
                <artifactId>feature-utils</artifactId>
                <version>1.0.0</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>ensure-wrap-bundle-version</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <featuresFilePath>${project.basedir}/src/main/feature/camel-features.xml</featuresFilePath>
                    <targetFeature>camel-google-mail</targetFeature> <!-- Optional, if no feature is specified, all features will be processed-->
                </configuration>
            </plugin>
